package com.scuplus.module.course.service.Impl;

import cn.hutool.core.collection.ConcurrentHashSet;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scuplus.common.exception.BusinessException;
import com.scuplus.common.exception.ErrorCode;
import com.scuplus.module.course.dto.CourseChooseVO;
import com.scuplus.module.course.dto.CourseDeleteVO;
import com.scuplus.module.course.entiy.Course;
import com.scuplus.module.course.entiy.CourseSelection;
import com.scuplus.module.course.mapper.CourseMapper;
import com.scuplus.module.course.mapper.CourseSelectionMapper;
import com.scuplus.module.course.service.CourseSeckill;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 抢课主服务：三档状态机
 *
 *   PHASE1 redis   —— Lua 原子判赢，赢家异步落库 MySQL，请求路径只剩 Redis（扛万人并发）
 *   PHASE2 closing —— 切换前的"收口"：关闭 Redis 判赢闸口，一次性清算账目（补齐缺行、清掉多余行）
 *   PHASE3 mysql   —— 纯 MySQL 权威：行锁判量 + 时间冲突判量后写入，Redis 不再参与
 *
 * 超卖根治：模式1"赢得即赢，绝不回补名额"（Lua 原子扣减锁定判赢数 ≤ 容量，异步/对账保证一人一行）；
 * 模式3 由 MySQL 事务内 COUNT 判量保证 ≤ 容量。整个设计里已经没有"还名额"这条路径。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourseSeckillImpl implements CourseSeckill {
    private final CourseMapper courseMapper;
    private final CourseSelectionMapper selectionMapper;
    private final StringRedisTemplate redisTemplate;
    @Qualifier("coursePersistExecutor")
    private final Executor coursePersistExecutor;
    private final TransactionTemplate transactionTemplate;
    private static final ConcurrentHashMap<Long, ReentrantLock> COURSE_LOCKS=new ConcurrentHashMap<>();
    private static final String PREFIX_STOCK = "stock";              // 缓存1：剩余名额
    private static final String PREFIX_USER_SLOTS = "user_slots";    // 缓存2：用户已占时间槽集合
    /** 不占时间槽的课程，course_slot 存这个占位值（nil 专指"缓存未初始化"，"no slot"专指"该课不占时间"，二者区分开） */
    private static final String NO_SLOT = "no slot";
    private static final String PREFIX_COURSE_SLOT = "course_slot";  // 缓存3：课程时间槽（class_time 相同=同一槽=冲突）
    private static final String PREFIX_COURSE_STUDENTS = "course_students"; // 缓存4：课程已选学生集合
    private static final String PREFIX_CAP = "cap";                  // 缓存5：课程原始容量（退课/对账用）
    private static final Set<Long> Valid_CourseIds=new ConcurrentHashSet<>();

    /** 抢课模式。收口档是关键：Redis主 → 关闸清算 → MySQL主 */
    public static final String MODE_REDIS = "redis";
    public static final String MODE_CLOSING = "closing";
    public static final String MODE_MYSQL = "mysql";
    private static final String KEY_MODE = "seckill:mode";
    private static final String KEY_MODE_SINCE = "seckill:mode_since";

    static final RedisScript<Long> Course_Choose_Script;
    static final RedisScript<Long> Course_Cancel_Script;


    static {
        // 抢课：三态原子判定（防重复 / 防时冲 / 防超卖），全部在一个 Lua 里，避免拆成多条命令产生竞态。
        // 注意：用文本块而非 + 拼接 —— 拼接会吃掉空格/换行，把 "then"+"return" 粘成 "thenreturn" 导致 Lua 编译失败（真实踩坑）。
        Course_Choose_Script = new DefaultRedisScript<>("""
                if redis.call('EXISTS',KEYS[1])==0 then return -3 end
                if redis.call('SISMEMBER',KEYS[4],ARGV[1]) ==1 then return -1 end
                local currentSlot=redis.call('GET',KEYS[3])
                if not currentSlot then return -5 end
                if currentSlot ~= 'no slot' then
                    if redis.call('SISMEMBER',KEYS[2],currentSlot)==1 then return -2 end
                end
                local remain=redis.call('DECR',KEYS[1])
                if remain<0 then redis.call('INCR',KEYS[1]) return -4 end
                redis.call('SADD',KEYS[4],ARGV[1])
                if currentSlot ~= 'no slot' then redis.call('SADD',KEYS[2],currentSlot) end
                return 0
                """, Long.class);

        // 退课：原子回补名额 + 释放"已选"和"时间槽"标记
        // KEYS[1]=stock KEYS[2]=userSlots KEYS[3]=courseSlot KEYS[4]=courseStudents KEYS[5]=cap
        Course_Cancel_Script = new DefaultRedisScript<>("""
                if redis.call('EXISTS',KEYS[1])==0 then return -3 end
                if redis.call('SISMEMBER',KEYS[4],ARGV[1])==0 then return -5 end
                local cap=redis.call('GET',KEYS[5])
                local stock=redis.call('GET',KEYS[1])
                if cap and stock and tonumber(stock) >= tonumber(cap) then return -4 end
                redis.call('INCR',KEYS[1])
                redis.call('SREM',KEYS[4],ARGV[1])
                local slot=redis.call('GET',KEYS[3])
                if slot then redis.call('SREM',KEYS[2],slot) end
                return 0
                """, Long.class);
    }
    /**启动初始化，将课程的id放入本地缓存，防止发生大规模异常id攻击发生*/
    @PostConstruct
    public void initCourseIdcache(){
        List<Course> courses=courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .eq(Course::getStatus,1));
        for(Course c : courses){
            Valid_CourseIds.add(c.getId());
        }
        log.info("合法课程ID加载完毕，共 {} 门", Valid_CourseIds.size());

    }



    /** 启动初始化：把库里"抢课中"课程的名额/时间槽灌进 Redis。
     *  用 setIfAbsent 而非 set —— 重启时绝不重置已被扣减的库存，否则"重启即复活名额"会超卖。 */
    @PostConstruct
    public void initCourseCache() {
        List<Course> courses = courseMapper.selectList(
                Wrappers.<Course>lambdaQuery().eq(Course::getStatus, 1));
        for (Course c : courses) {
            redisTemplate.opsForValue().setIfAbsent(PREFIX_STOCK + ":{" + c.getId() + "}", String.valueOf(c.getCapacity()));
            redisTemplate.opsForValue().setIfAbsent(PREFIX_CAP + ":{" + c.getId() + "}", String.valueOf(c.getCapacity()));
            // 有 class_time 存真实时间槽；没有则存 "no slot"（保证非 nil，让"缓存没接上"可被 -5 识别）
            redisTemplate.opsForValue().setIfAbsent(PREFIX_COURSE_SLOT + ":{" + c.getId() + "}",
                    c.getClassTime() != null ? c.getClassTime() : NO_SLOT);
            ensureCourseStudents(c.getId());
        }
        // 模式默认 Redis 主；已存在（清库前的旧事件）则不覆盖
        redisTemplate.opsForValue().setIfAbsent(KEY_MODE, MODE_REDIS);
        redisTemplate.opsForValue().setIfAbsent(KEY_MODE_SINCE, String.valueOf(System.currentTimeMillis()));
        log.info("抢课缓存初始化完成：{} 门课程已灌入 Redis，当前模式 {}", courses.size(), currentMode());
    }

    /** course_students 集合缺失时创建空集合：Lua 第一行 EXISTS(KEYS[4]) 依赖它存在，
     *  否则每门课的第一次抢课都会因集合未建而恒返回 -3（"课程不存在"）。 */
    private void ensureCourseStudents(Long courseId) {
        String setKey = PREFIX_COURSE_STUDENTS + ":{" + courseId + "}";
        if (!redisTemplate.hasKey(setKey)) {
            redisTemplate.opsForSet().add(setKey, "init");
            redisTemplate.opsForSet().remove(setKey, "init");
        }
    }

    /** 当前抢课模式 */
    public String currentMode() {
        String mode = redisTemplate.opsForValue().get(KEY_MODE);
        return mode == null ? MODE_REDIS : mode;
    }

    @Override
    public CourseChooseVO choose(Long userId, Long courseId) {
        if (!Valid_CourseIds.contains(courseId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "课程不存在");
        }
        String mode = currentMode();
        if (MODE_CLOSING.equals(mode)) {
            // 收口：暂停 Redis 判赢，切换前做最后一次清算；请用户稍后重试走 MySQL 主
            return closingVo();
        }
        if (MODE_MYSQL.equals(mode)) {
            return chooseMySqlAuthoritative(userId, courseId);
        }
        return chooseRedisAuthoritative(userId, courseId);
    }

    /** PHASE1(Redis主)：Lua 原子判赢 → 异步落库，赢得即赢，绝不回补名额 */
    private CourseChooseVO chooseRedisAuthoritative(Long userId, Long courseId) {
        String stock = PREFIX_STOCK + ":{" + courseId + "}";
        String userSlots = PREFIX_USER_SLOTS + ":{" + userId + "}";
        String courseSlot = PREFIX_COURSE_SLOT + ":{" + courseId + "}";
        String courseStudents = PREFIX_COURSE_STUDENTS + ":{" + courseId + "}";
        List<String> keys = Arrays.asList(stock, userSlots, courseSlot, courseStudents);
        log.debug("抢课请求开始：userId={}, courseId={}", userId, courseId);
        long start = System.currentTimeMillis();
        Long result = redisTemplate.execute(Course_Choose_Script, keys, String.valueOf(userId));
        long cost = System.currentTimeMillis() - start;

// 1. 只有 Redis 耗时异常（比如网络抖动>50ms）才打 WARN
        if (cost > 50) {
            log.warn("Redis执行耗时过长：{}ms, courseId={}", cost, courseId);
        }
        // 缓存可能被清/漏初始化：-3（course_students 缺失）/ -5（course_slot 缺失）→ 查库重建缓存后重试一次
        if (result == -3 || result == -5) {
            log.warn("课程缓存 key 缺失（result={}），查库重建后重试：courseId={}", result, courseId);
            reloadCourseCache(courseId);
            result = redisTemplate.execute(Course_Choose_Script, keys, String.valueOf(userId));
        }
        if (result == -3) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "课程不存在，请重新选课");
        }
        if (result == -1) {
            throw new BusinessException(ErrorCode.CONFLICT, "已选课，请勿重复选课");
        }
        if (result == -2) {
            throw new BusinessException(ErrorCode.CONFLICT, "选课时间与已选时间冲突，请重新选课");
        }
        if (result == -5) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "课程缓存未初始化，请稍后重试");
        }
        if (result == -4) {
            throw new BusinessException(ErrorCode.CONFLICT, "抢课失败，课程已满");
        }

        // Redis 已原子"宣布胜利"。异步落库：输赢由 Redis 定，MySQL 只负责记，失败交给对账补齐
        asyncPersist(courseId, userId);
        return successVo();
    }

    /** 异步落库：MySQL 慢写移出请求线程；拒绝/失败都交给对账补齐，绝不阻塞请求、绝不回补名额 */
    private void asyncPersist(Long courseId, Long userId) {
        try {
            coursePersistExecutor.execute(() -> {
                CourseSelection sel = toEntiy(courseId, userId, 1, new CourseSelection());
                try {
                    selectionMapper.insert(sel);
                } catch (DuplicateKeyException e) {
                    // 幂等：行已存在（并发重试 / 对账已补）
                } catch (Exception e) {
                    log.error("异步落库失败，交给对账补齐：userId={}, courseId={}, err={}", userId, courseId, e.getMessage());
                }
            });
        } catch (RejectedExecutionException e) {
            // 队列满：直接放弃，Redis 判的赢家由对账补齐
            log.warn("异步落库队列已满，交给对账补齐：userId={}, courseId={}", userId, courseId);
        }
    }

    /** PHASE3(MySQL主)：纯 MySQL 事务。行锁串行化 → 已选人数判量 → 时间冲突判量 → 写入。Redis 不参与。 */
    private CourseChooseVO chooseMySqlAuthoritative(Long userId, Long courseId) {
        try {
            transactionTemplate.execute(tx -> {
                Course course = courseMapper.selectByIdForUpdate(courseId);
                if (course == null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "课程不存在");
                }
                // ① 先看自己是否已选：已选＝幂等成功，不占新名额。
                //    必须放在容量/冲突判断之前——否则冲突查询会把自己刚选的这堂课也数进去，误报"时间冲突"
                CourseSelection existing = selectionMapper.selectOne(Wrappers.<CourseSelection>lambdaQuery()
                        .eq(CourseSelection::getUserId, userId)
                        .eq(CourseSelection::getCourseId, courseId));
                if (existing != null && existing.getStatus() == 1) {
                    return 1;
                }
                // ② 判量：已选人数 < 容量（行锁保证并发的两次不会都通过）
                Long selected = selectionMapper.selectCount(Wrappers.<CourseSelection>lambdaQuery()
                        .eq(CourseSelection::getCourseId, courseId)
                        .eq(CourseSelection::getStatus, 1));
                if ((selected == null ? 0 : selected) >= course.getCapacity()) {
                    throw new CourseFullException();
                }
                // ③ 判时间冲突：已有已选课程里没有撞上课时间的（自己的 status=1 已在上一步排除）
                if (course.getClassTime() != null) {
                    if (selectionMapper.selectConflictedCount(userId, course.getClassTime()) > 0) {
                        throw new BusinessException(ErrorCode.CONFLICT, "选课时间与已选时间冲突，请重新选课");
                    }
                }
                // ④ 写入：已退/失败→重选改回已选；新用户→插入
                if (existing != null) {
                    selectionMapper.update(null, Wrappers.<CourseSelection>lambdaUpdate()
                            .eq(CourseSelection::getId, existing.getId())
                            .set(CourseSelection::getStatus, 1));
                    return 1;
                }
                selectionMapper.insert(toEntiy(courseId, userId, 1, new CourseSelection()));
                return 1;
            });
        } catch (BusinessException e) {
            throw e; // 明确的业务结论（课程不存在/时间冲突）直接透传
        } catch (CourseFullException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "抢课失败，课程已满");
        } catch (DuplicateKeyException e) {
            // 并发下撞唯一键：另一个请求已插好，用户确持名额
            log.warn("PHASE3 并发重选唯一键兜底，按成功处理：userId={}, courseId={}", userId, courseId);
            return successVo();
        } catch (Exception e) {
            if (isAmbiguous(e)) {
                // 超时/断连：结果未知。MySQL 是权威，把它交给查询/对账去收敛，绝不在客户端翻案
                log.warn("PHASE3 落库结果未知，返回处理中：userId={}, courseId={}, err={}", userId, courseId, e.getMessage());
                return processingVo();
            }
            log.error("PHASE3 抢课异常：userId={}, courseId={}", userId, courseId, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "抢课失败，请稍后重试");
        }
        return successVo();
    }

    @Override
    public CourseDeleteVO delete(Long userId, Long courseId) {
        if (MODE_MYSQL.equals(currentMode())) {
            return deleteMySqlAuthoritative(userId, courseId);
        }
        String stock = PREFIX_STOCK + ":{" + courseId + "}";
        String userSlots = PREFIX_USER_SLOTS + ":{" + userId + "}";
        String courseSlot = PREFIX_COURSE_SLOT + ":{" + courseId + "}";
        String courseStudents = PREFIX_COURSE_STUDENTS + ":{" + courseId + "}";
        String cap = PREFIX_CAP + ":{" + courseId + "}";
        List<String> keys = Arrays.asList(stock, userSlots, courseSlot, courseStudents, cap);

        log.debug("退课请求开始：userId={}, courseId={}", userId, courseId);
        long start = System.currentTimeMillis();
        Long result = redisTemplate.execute(Course_Cancel_Script, keys, String.valueOf(userId));
        long cost = System.currentTimeMillis() - start;
        // Redis 慢调用才告警，正常静默
        if (cost > 50) {
            log.warn("退课 Redis 执行耗时过长：{}ms, courseId={}", cost, courseId);
        }
        if (result == -3) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在或未开放");
        }
        if (result == -5) {
            throw new BusinessException(ErrorCode.CONFLICT, "未选该课程或已退课");
        }
        if (result == -4) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "库存数据异常，已取消退课");
        }

        // Redis 已原子回补名额；MySQL 只做状态流转 SUCCESS(1) → CANCELED(2)
        // 条件更新 WHERE status=1：并发连点退课时只有一次能成功，防双退
        long dbStart = System.currentTimeMillis();
        int updated = selectionMapper.update(null, Wrappers.<CourseSelection>lambdaUpdate()
                .eq(CourseSelection::getUserId, userId)
                .eq(CourseSelection::getCourseId, courseId)
                .eq(CourseSelection::getStatus, 1)
                .set(CourseSelection::getStatus, 2));
        long dbCost = System.currentTimeMillis() - dbStart;
        if (dbCost > 100) {
            log.warn("退课 MySQL 更新耗时过长：{}ms, userId={}, courseId={}", dbCost, userId, courseId);
        } else {
            log.debug("退课 MySQL 更新耗时：{}ms", dbCost); // 默认不输出
        }
        // updated==0（MySQL 还不是"已选"态，比如对账还没补上行）→ 残窗口，Redis 名额已回补（安全方向），交给对账
        if (updated == 0) {
            log.warn("退课落库未命中（updated=0），Redis 名额已回补，交给对账兜底：userId={}, courseId={}", userId, courseId);
        }

        CourseDeleteVO vo = new CourseDeleteVO();
        vo.setStatus("2");
        vo.setMessage("退课成功");
        return vo;
    }

    /** PHASE3 退课：纯 MySQL，已选翻成已退。判量用 COUNT(status=1)，退课即自然释放名额，无需 Redis */
    private CourseDeleteVO deleteMySqlAuthoritative(Long userId, Long courseId) {
        int updated = selectionMapper.update(null, Wrappers.<CourseSelection>lambdaUpdate()
                .eq(CourseSelection::getUserId, userId)
                .eq(CourseSelection::getCourseId, courseId)
                .eq(CourseSelection::getStatus, 1)
                .set(CourseSelection::getStatus, 2));
        CourseDeleteVO vo = new CourseDeleteVO();
        vo.setStatus("2");
        vo.setMessage(updated == 1 ? "退课成功" : "未选该课程或已退课");
        return vo;
    }

    /** 对账入口：按当前模式分叉。收口档的清算由 CourseModeJob 单独驱动。 */
    public void reconcile() {
        String mode = currentMode();
        if (MODE_REDIS.equals(mode)) {
            reconcileRedisFirst();
        } else if (MODE_MYSQL.equals(mode)) {
            reconcileMySqlFirst();
        }
        // closing 期间：Redis 判赢已冻结，交给 CourseModeJob.reconcileClosing 做最后清算
    }

    /** 对账(PHASE1, Redis主)：只补不删。Redis 判赢但 MySQL 缺行 → 补一行（异步落库的兜底网） */
    private void reconcileRedisFirst() {
        int added = 0;
        for (String key : redisTemplate.keys(PREFIX_COURSE_STUDENTS + ":*")) {
            Long courseId = courseIdOf(key);
            for (String uid : redisTemplate.opsForSet().members(key)) {
                if ("init".equals(uid)) {
                    continue; // 集合初始化占位跳过
                }
                Long userId = Long.valueOf(uid);
                boolean inDb = selectionMapper.selectCount(Wrappers.<CourseSelection>lambdaQuery()
                        .eq(CourseSelection::getUserId, userId)
                        .eq(CourseSelection::getCourseId, courseId)
                        .eq(CourseSelection::getStatus, 1)) > 0;
                if (!inDb) {
                    try {
                        selectionMapper.insert(toEntiy(courseId, userId, 1, new CourseSelection()));
                        added++;
                    } catch (DuplicateKeyException e) {
                        // 与异步落库并发，行已存在即可
                    }
                }
            }
        }
        if (added > 0) {
            log.info("对账(PHASE1-Redis主)：补齐 MySQL 选课行 {} 行", added);
        }
    }

    /** 收口清算(closing)：Redis 判赢集合已冻结，此时做最后一次拉平——补齐缺行、清掉多余行 */
    public void reconcileClosing() {
        int added = 0, cleared = 0;
        // 补：Redis 判赢、MySQL 缺行 → 补齐
        for (String key : redisTemplate.keys(PREFIX_COURSE_STUDENTS + ":*")) {
            Long courseId = courseIdOf(key);
            for (String uid : redisTemplate.opsForSet().members(key)) {
                if ("init".equals(uid)) {
                    continue;
                }
                Long userId = Long.valueOf(uid);
                boolean inDb = selectionMapper.selectCount(Wrappers.<CourseSelection>lambdaQuery()
                        .eq(CourseSelection::getUserId, userId)
                        .eq(CourseSelection::getCourseId, courseId)
                        .eq(CourseSelection::getStatus, 1)) > 0;
                if (!inDb) {
                    try {
                        selectionMapper.insert(toEntiy(courseId, userId, 1, new CourseSelection()));
                        added++;
                    } catch (DuplicateKeyException e) {
                    }
                }
            }
        }
        // 清：MySQL 有 status=1 行但 Redis 无对应标记 → 判定"非判赢"，翻成选课失败(3)
        //     （Redis主模式下 MySQL 只是镜像，镜像里多出来的行不算数）
        for (CourseSelection sel : selectionMapper.selectList(Wrappers.<CourseSelection>lambdaQuery()
                .eq(CourseSelection::getStatus, 1))) {
            String key = PREFIX_COURSE_STUDENTS + ":{" + sel.getCourseId() + "}";
            if (Boolean.FALSE.equals(redisTemplate.opsForSet().isMember(key, String.valueOf(sel.getUserId())))) {
                selectionMapper.update(null, Wrappers.<CourseSelection>lambdaUpdate()
                        .eq(CourseSelection::getId, sel.getId())
                        .eq(CourseSelection::getStatus, 1)
                        .set(CourseSelection::getStatus, 3));
                cleared++;
            }
        }
        if (added > 0 || cleared > 0) {
            log.info("收口清算：补齐 {} 行，判定失败 {} 行", added, cleared);
        }
    }

    /** 切换前账目校验：每门课 |Redis course_students| == |MySQL status=1 行数| */
    public boolean isBooksMatched() {
        for (Course c : courseMapper.selectList(Wrappers.<Course>lambdaQuery().eq(Course::getStatus, 1))) {
            Long dbCount = selectionMapper.selectCount(Wrappers.<CourseSelection>lambdaQuery()
                    .eq(CourseSelection::getCourseId, c.getId())
                    .eq(CourseSelection::getStatus, 1));
            long redis = redisSizeOf(c.getId());
            if (redis != (dbCount == null ? 0 : dbCount)) {
                return false;
            }
        }
        return true;
    }

    /** 进入收口：关闭 Redis 判赢闸口（之后的 choose 请求返回"稍后"），冻结赢家集合准备清算 */
    public void beginClosing() {
        redisTemplate.opsForValue().set(KEY_MODE, MODE_CLOSING);
        log.info("抢课进入收口：暂停 Redis 判赢，开始切换前清算");
    }

    /** 切换完成：MySQL 主。此后 choose 走纯 MySQL 事务，Redis 仅展示 */
    public void flipToMySql() {
        redisTemplate.opsForValue().set(KEY_MODE, MODE_MYSQL);
        log.info("抢课切换完成：MySQL 主");
    }

    /** 对账(PHASE3, MySQL主)：以 MySQL 为准收敛 Redis 展示口径。
     *  方向1 — Redis 有"已选"标记、MySQL 无 status=1 行：清 Redis 标记 + 还名额（MySQL 判未选）
     *  方向2 — MySQL 有 status=1 行、Redis 无标记：补 Redis 标记 + 用"容量-已选数"重算库存
     */
    private void reconcileMySqlFirst() {
        // ---- 方向1：清理 Redis 幽灵标记 ----
        int ghostCleaned = 0;
        // 生产大集群用 SCAN 代替 KEYS（KEYS O(N) 会阻塞 Redis）；本 demo 200 门课可接受
        Set<String> studentKeys = redisTemplate.keys(PREFIX_COURSE_STUDENTS + ":*");
        for (String key : studentKeys) {
            Long courseId = courseIdOf(key);
            Set<String> users = redisTemplate.opsForSet().members(key);
            for (String userIdStr : users) {
                if ("init".equals(userIdStr)) {
                    continue;
                }
                Long userId = Long.valueOf(userIdStr);
                boolean inDb = selectionMapper.selectCount(Wrappers.<CourseSelection>lambdaQuery()
                        .eq(CourseSelection::getUserId, userId)
                        .eq(CourseSelection::getCourseId, courseId)
                        .eq(CourseSelection::getStatus, 1)) > 0;
                if (!inDb) {
                    Long rb = runCancelScript(userId, courseId); // 复用退课脚本：清标记+还名额+放时间槽
                    if (rb != null && rb == 0) {
                        ghostCleaned++;
                        log.warn("对账-PHASE3-方向1：MySQL 无已选记录，清理 Redis 幽灵标记 userId={}, courseId={}", userId, courseId);
                    } else {
                        log.error("对账-PHASE3-方向1清理失败（rb={}），留待下次对账：userId={}, courseId={}", rb, userId, courseId);
                    }
                }
            }
        }
        if (ghostCleaned > 0) {
            log.info("对账-PHASE3-方向1完成：清理幽灵标记 {} 个", ghostCleaned);
        }

        // ---- 方向2：给 MySQL 已选但 Redis 缺标记的补标记 + 重算库存 ----
        List<CourseSelection> selections = selectionMapper.selectList(
                Wrappers.<CourseSelection>lambdaQuery().eq(CourseSelection::getStatus, 1));
        Set<Long> needRecalc = new HashSet<>();
        int backfilled = 0;
        for (CourseSelection sel : selections) {
            String setKey = PREFIX_COURSE_STUDENTS + ":{" + sel.getCourseId() + "}";
            if (Boolean.FALSE.equals(redisTemplate.opsForSet().isMember(setKey, String.valueOf(sel.getUserId())))) {
                redisTemplate.opsForSet().add(setKey, String.valueOf(sel.getUserId()));
                backfilled++;
                needRecalc.add(sel.getCourseId());
                log.warn("对账-PHASE3-方向2：MySQL 有已选但 Redis 无标记，补标记 userId={}, courseId={}",
                        sel.getUserId(), sel.getCourseId());
            }
        }
        if (!needRecalc.isEmpty()) {
            // 库存 = 容量 - 已选人数（容量以 MySQL 为准，不靠 INCR 累加，避免长期漂移）
            Map<Long, Integer> capMap = courseMapper.selectList(null).stream()
                    .collect(Collectors.toMap(Course::getId, c -> c.getCapacity().intValue()));
            for (Long courseId : needRecalc) {
                Long taken = redisTemplate.opsForSet().size(PREFIX_COURSE_STUDENTS + ":{" + courseId + "}");
                int stock = capMap.getOrDefault(courseId, 0) - (taken == null ? 0 : taken.intValue());
                redisTemplate.opsForValue().set(PREFIX_STOCK + ":{" + courseId + "}", String.valueOf(Math.max(stock, 0)));
            }
            log.info("对账-PHASE3-方向2完成：补 Redis 标记 {} 个，重算库存 {} 门", backfilled, needRecalc.size());
        }
    }

    /** 歧义失败判定：超时/断连类异常，说明"服务端到底提交没有"未知，绝不能按"失败"去补偿/还名额 */
    private boolean isAmbiguous(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof QueryTimeoutException
                    || t instanceof CannotGetJdbcConnectionException
                    || t instanceof DataAccessResourceFailureException
                    || t.getClass().getName().contains("CommunicationsException")
                    || t.getClass().getName().contains("MySQLNonTransientConnectionException")) {
                return true;
            }
        }
        return false;
    }

    private CourseChooseVO successVo() {
        CourseChooseVO vo = new CourseChooseVO();
        vo.setStatus(1);
        vo.setMessage("选课成功");
        return vo;
    }

    private CourseChooseVO processingVo() {
        CourseChooseVO vo = new CourseChooseVO();
        vo.setStatus(0);
        vo.setMessage("选课结果确认中，请刷新重试");
        return vo;
    }

    private CourseChooseVO closingVo() {
        CourseChooseVO vo = new CourseChooseVO();
        vo.setStatus(0);
        vo.setMessage("选课正在切换阶段，请稍后重试");
        return vo;
    }

    /** PHASE3 判量满员的哨兵异常（负责触发事务回滚） */
    private static final class CourseFullException extends RuntimeException {
    }

    /** 该课程 Redis 已选集合的当前大小 */
    private long redisSizeOf(Long courseId) {
        Long size = redisTemplate.opsForSet().size(PREFIX_COURSE_STUDENTS + ":{" + courseId + "}");
        return size == null ? 0 : size;
    }

    /** 原子补偿：落库失败时复用退课脚本，撤销 Redis 的"已选"标记与名额扣减，让用户可重试而不是卡死。
     *  补偿自身也可能失败 → Redis 残留"已选"标记，交给对账方向1兜底。 */
    private void compensate(Long userId, Long courseId) {
        Long rb;
        try {
            rb = runCancelScript(userId, courseId);
        } catch (Exception rbEx) {
            rb = null;
            log.error("补偿执行异常：userId={}, courseId={}", userId, courseId, rbEx);
        }
        if (rb == null) {
            log.error("补偿失败（Redis 异常），残留已选标记，交给对账方向1");
        } else if (rb == 0) {
            log.warn("补偿成功，名额已回补，用户可重试");
        } else if (rb == -5) {
            log.warn("补偿时用户已不在已选集合（可能已并发退课），无需补偿");
        } else {
            log.error("补偿返回异常码 {}，残留已选标记，交给对账方向1", rb);
        }
    }

    /** 复用退课脚本：原子回补名额 + 清已选标记 + 释放时间槽，返回脚本结果码 */
    private Long runCancelScript(Long userId, Long courseId) {
        List<String> cancelKeys = Arrays.asList(
                PREFIX_STOCK + ":{" + courseId + "}",
                PREFIX_USER_SLOTS + ":{" + userId + "}",
                PREFIX_COURSE_SLOT + ":{" + courseId + "}",
                PREFIX_COURSE_STUDENTS + ":{" + courseId + "}",
                PREFIX_CAP + ":{" + courseId + "}");
        return redisTemplate.execute(Course_Cancel_Script, cancelKeys, String.valueOf(userId));
    }

    /** "course_students:{123}" → 123 */
    private Long courseIdOf(String studentsKey) {
        String s = studentsKey.substring(studentsKey.lastIndexOf(':') + 1);
        return Long.valueOf(s.substring(1, s.length() - 1));
    }

    /** 缓存自愈：Lua 返回 -3/-5 或列表页读取触发时查库重建该课程缓存。
     *  三重检查（快速路径 → 加锁 → 锁内二查）：只有"第一个真正看到缺失"的线程才查库，其余等完直接拿值。
     *  关键：完整性必须看【stock + course_slot + course_students 三个 key】——
     *   只看 stock 会在"course_students/course_slot 缺失而 stock 还在"时漏修（choose 的 -3/-5 依旧）；
     *   stock 用 count 重算 + setIfAbsent，绝不重置回满容量（防超卖）。 */
    private void reloadCourseCache(Long courseId) {
        if (isCourseCacheComplete(courseId)) {
            return;
        }
        ReentrantLock reentrantLock = COURSE_LOCKS.computeIfAbsent(courseId, k -> new ReentrantLock());
        reentrantLock.lock();
        try {
            // 二次检查：等在锁上的线程可能已重建完
            if (isCourseCacheComplete(courseId)) {
                return;
            }
            Course c = courseMapper.selectById(courseId);
            if (c == null) {
                log.warn("缓存 key 缺失且课程 {} 库中也查不到，无法自愈", courseId);
                return;
            }
            Long chosen = selectionMapper.selectCount(Wrappers.<CourseSelection>lambdaQuery()
                    .eq(CourseSelection::getCourseId, courseId)
                    .eq(CourseSelection::getStatus, 1));
            // 库存 = 容量 - 已选（count 重算，防超卖）；setIfAbsent 不覆盖已扣减库存
            redisTemplate.opsForValue().setIfAbsent(PREFIX_STOCK + ":{" + courseId + "}",
                    String.valueOf(Math.max(c.getCapacity() - (chosen == null ? 0 : chosen), 0)));
            redisTemplate.opsForValue().setIfAbsent(PREFIX_CAP + ":{" + courseId + "}", String.valueOf(c.getCapacity()));
            redisTemplate.opsForValue().setIfAbsent(PREFIX_COURSE_SLOT + ":{" + courseId + "}",
                    c.getClassTime() != null ? c.getClassTime() : NO_SLOT);
            ensureCourseStudents(courseId);
            log.info("课程 {} 缓存已自愈重建", courseId);
        } finally {
            reentrantLock.unlock();
        }
    }

    /** 该课程缓存是否"初始化完整"：stock/cap/course_slot 三个 string key 在即可。
     *  注意不含 course_students —— 空集合会被 Redis 自动删除，恒 EXISTS=0，不能作为"是否初始化"的判断依据。 */
    private boolean isCourseCacheComplete(Long courseId) {
        return redisTemplate.hasKey(PREFIX_STOCK + ":{" + courseId + "}")
                && redisTemplate.hasKey(PREFIX_CAP + ":{" + courseId + "}")
                && redisTemplate.hasKey(PREFIX_COURSE_SLOT + ":{" + courseId + "}");
    }

    /** 读取某课实时剩余名额：缓存缺失走 DCL 自愈（count 重算）。DB 也挂读不到时返回 null，交给上层降级。 */
    public Integer remainingOf(Long courseId) {
        reloadCourseCache(courseId);
        String v = redisTemplate.opsForValue().get(PREFIX_STOCK + ":{" + courseId + "}");
        return v == null ? null : Integer.parseInt(v);
    }

    /** 列表页批量取库存时构造 stock key（统一 key 格式，避免散落各处写死） */
    public static String stockKeyOf(Long courseId) {
        return PREFIX_STOCK + ":{" + courseId + "}";
    }

    private CourseSelection toEntiy(Long courseId, Long userId, Integer status, CourseSelection courseSelection) {
        courseSelection.setCourseId(courseId);
        courseSelection.setUserId(userId);
        courseSelection.setStatus(status);
        return courseSelection;
    }
}