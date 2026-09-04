package com.scuplus.module.course.service.Impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.scuplus.common.exception.BusinessException;
import com.scuplus.common.exception.ErrorCode;
import com.scuplus.common.result.PageResult;
import com.scuplus.module.course.dto.CourseVO;
import com.scuplus.module.course.entiy.Course;
import com.scuplus.module.course.entiy.CourseSelection;
import com.scuplus.module.course.mapper.CourseMapper;
import com.scuplus.module.course.mapper.CourseSelectionMapper;
import com.scuplus.module.course.service.CourseView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 课程浏览（抢课页列表）
 *
 * 读写两条链完全分离：
 *  - 静态（name/teacher/classTime/capacity）→ Caffeine，refreshAfterWrite(30s) 异步单飞刷新，
 *    刷新失败自动保留旧值（"允许读脏数据"的降级）；expireAfterWrite(24h) 硬兜底防无限旧。
 *  - 动态（已选人数 / 当前用户是否已选）→ Redis，一个 Lua 批量取本页全部课程（一次 RTT）；
 *    Redis 挂了 → DB GROUP COUNT 兜底；DB 也挂 → selectedCount 降级 -1（名额紧张），不裸抛 500。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseViewImpl implements CourseView {

    private final CourseMapper courseMapper;
    private final CourseSelectionMapper selectionMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String PREFIX_COURSE_STUDENTS = "course_students";
    private static final String CACHE_KEY = "active-courses";

    /** 降级计数：累计说明系统真的在抖，方便告警（demo 只打日志） */
    private final AtomicInteger degradeCount = new AtomicInteger();

    /** 静态课程列表缓存：只装 status=1（抢课中）的课。
     *  用 LoadingCache（build(loader)），get 首次同步加载、随后自动进入 refreshAfterWrite 异步单飞刷新 */
    private final LoadingCache<String, List<Course>> courseCache = Caffeine.newBuilder()
            .maximumSize(2)
            .expireAfterWrite(24, TimeUnit.HOURS)       // 硬兜底：refresh 全失败也不至于无限旧
            .refreshAfterWrite(30, TimeUnit.SECONDS)    // 异步单飞刷新，读时触发，不阻塞请求
            .build(this::loadActiveCourses);

    private List<Course> loadActiveCourses(String key) {
        return courseMapper.selectList(Wrappers.<Course>lambdaQuery().eq(Course::getStatus, 1));
    }

    /** 批量动态数据：一次 EVAL 拿本页每门课 已选人数(SCARD) + 该用户是否已选(SISMEMBER)。
     *  结果按 [card1, member1, card2, member2...] 交替返回 */
    static final RedisScript<List> BATCH_VIEW_SCRIPT = new DefaultRedisScript<>("""
            local res = {}
            for i = 1, #KEYS do
                res[(i - 1) * 2 + 1] = redis.call('SCARD', KEYS[i])
                res[(i - 1) * 2 + 2] = redis.call('SISMEMBER', KEYS[i], ARGV[1])
            end
            return res
            """, List.class);

    @Override
    public PageResult<CourseVO> list(Long userid, int page, int size, String className, String teacherName) {
        List<Course> all;
        try {
            all = courseCache.get(CACHE_KEY);
        } catch (Exception e) {
            // 静态缓存 + DB 都不可用 → 友好业务错误，绝不裸抛 500
            log.error("课程静态数据加载失败（缓存/DB 不可用）", e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "系统繁忙，请稍后重试");
        }
        if (all.isEmpty()) {
            return PageResult.of(new ArrayList<>(), 0);
        }

        // 关键词过滤：课程名 / 老师名，忽略大小写模糊匹配（Hutool）
        if (StrUtil.isNotBlank(className)) {
            String kw = className.trim();
            all = all.stream().filter(c -> StrUtil.containsIgnoreCase(c.getName(), kw)).toList();
        }
        if (StrUtil.isNotBlank(teacherName)) {
            String kw = teacherName.trim();
            all = all.stream().filter(c -> StrUtil.containsIgnoreCase(c.getTeacher(), kw)).toList();
        }
        int total = all.size();

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        int from = Math.min((safePage - 1) * safeSize, total);
        int to = Math.min(from + safeSize, total);
        List<Course> pageCourses = all.subList(from, to);

        // 动态：已选人数 + 是否已选（Redis 批量 → DB 兜底 → -1 降级）
        Map<Long, Long> counts = new HashMap<>();
        Map<Long, Boolean> flags = new HashMap<>();
        boolean dynamicOk = loadDynamic(pageCourses, userid, counts, flags);

        List<CourseVO> items = new ArrayList<>(pageCourses.size());
        for (Course c : pageCourses) {
            CourseVO vo = new CourseVO();
            vo.setId(c.getId());
            vo.setName(c.getName());
            vo.setTeacher(c.getTeacher());
            vo.setClassTime(c.getClassTime());
            vo.setCapacity(c.getCapacity());
            if (dynamicOk) {
                vo.setSelectedCount(counts.getOrDefault(c.getId(), 0L));
                vo.setIfchoosen(flags.getOrDefault(c.getId(), false));
            } else {
                // 两级都失败：-1 表示"名额未知"，不虚报精确数字
                vo.setSelectedCount(-1L);
                vo.setIfchoosen(false);
            }
            items.add(vo);
        }
        return PageResult.of(items, total);
    }

    /** 返回 true=拿到动态数据；false=Redis+DB 都失败，已降级（selectedCount=-1） */
    private boolean loadDynamic(List<Course> pageCourses, Long userid,
                                Map<Long, Long> counts, Map<Long, Boolean> flags) {
        try {
            List<String> keys = pageCourses.stream()
                    .map(c -> PREFIX_COURSE_STUDENTS + ":{" + c.getId() + "}")
                    .toList();
            List<Long> vals = redisTemplate.execute(BATCH_VIEW_SCRIPT, keys, String.valueOf(userid));
            for (int i = 0; i < pageCourses.size(); i++) {
                counts.put(pageCourses.get(i).getId(), vals.get(i * 2));
                flags.put(pageCourses.get(i).getId(), vals.get(i * 2 + 1) == 1L);
            }
            return true;
        } catch (Exception redisEx) {
            log.warn("列表动态数据 Redis 读取失败，改用 DB 兜底：{}", redisEx.getMessage());
            try {
                List<Long> ids = pageCourses.stream().map(Course::getId).toList();
                // 已选人数：一次 GROUP BY 查出本页全部课程
                List<CourseSelection> rows = selectionMapper.selectList(Wrappers.<CourseSelection>lambdaQuery()
                        .in(CourseSelection::getCourseId, ids)
                        .eq(CourseSelection::getStatus, 1));
                for (CourseSelection r : rows) {
                    counts.merge(r.getCourseId(), 1L, Long::sum);
                }
                // 是否已选：该用户在页面课程里的已选行
                List<CourseSelection> mine = selectionMapper.selectList(Wrappers.<CourseSelection>lambdaQuery()
                        .eq(CourseSelection::getUserId, userid)
                        .in(CourseSelection::getCourseId, ids)
                        .eq(CourseSelection::getStatus, 1));
                for (CourseSelection r : mine) {
                    flags.put(r.getCourseId(), true);
                }
                return true;
            } catch (Exception dbEx) {
                log.error("列表动态数据 DB 兜底也失败，降级 selectedCount=-1：", dbEx);
                log.warn("列表降级计数：{}", degradeCount.incrementAndGet());
                return false;
            }
        }
    }
}