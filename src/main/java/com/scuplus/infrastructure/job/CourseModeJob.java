package com.scuplus.infrastructure.job;

import com.scuplus.module.course.service.Impl.CourseSeckillImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 抢课模式推进任务：Redis主 → 收口清算 → MySQL主
 *
 * 为什么需要"收口"这档：
 *   直接切模式有漏洞——切到 MySQL 主那一瞬，可能还有 Redis 已判赢但行没落进 MySQL 的赢家，
 *   MySQL 主模式下没人给他补行 → 丢课。收口档先把 Redis 判赢的口子关掉（新请求"稍后再试"），
 *   让赢家集合冻结，再一次性清算（补齐缺行、清掉多余行），账完全拉平后才放开 MySQL 主。
 *
 * 推进条件：
 *   redis(主)   --距开始满10分钟-->   收口(关闸+清算)   --账拉平-->   mysql(主)
 *   "账拉平" = 每门课 |Redis course_students| == |MySQL status=1 行数|
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CourseModeJob {

    private static final String KEY_MODE = "seckill:mode";
    private static final String KEY_MODE_SINCE = "seckill:mode_since";
    private static final long PHASE1_DURATION_MS = 10 * 60 * 1000L;

    private final CourseSeckillImpl courseSeckill;
    private final StringRedisTemplate redisTemplate;

    /** 每 15s 推进一次模式状态机 */
    @Scheduled(fixedDelay = 15_000L)
    public void advanceMode() {
        try {
            String mode = courseSeckill.currentMode();
            if (CourseSeckillImpl.MODE_MYSQL.equals(mode)) {
                return; // 已是 MySQL 主
            }
            if (CourseSeckillImpl.MODE_CLOSING.equals(mode)) {
                // 收口中：清算一次，账平就切 MySQL 主；没平继续等（对账已在补）
                courseSeckill.reconcileClosing();
                if (courseSeckill.isBooksMatched()) {
                    courseSeckill.flipToMySql();
                } else {
                    log.warn("收口清算进行中：账未拉平，等待下一次对账");
                }
                return;
            }
            // Redis 主：满 10 分钟 → 进入收口（可调 PHASE1_DURATION_MS）
            String since = redisTemplate.opsForValue().get(KEY_MODE_SINCE);
            if (since == null) {
                return;
            }
            long elapsed = System.currentTimeMillis() - Long.parseLong(since);
            if (elapsed >= PHASE1_DURATION_MS) {
                courseSeckill.beginClosing();
            }
        } catch (Exception e) {
            log.error("抢课模式推进任务异常", e);
        }
    }
}