package com.scuplus.infrastructure.job;

import com.scuplus.module.course.service.Impl.CourseSeckillImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 抢课模块对账定时任务
 *
 * 每 5 分钟把 Redis 与 MySQL 的选课状态收敛一致（以 MySQL 为准）。
 * 需要它的原因：抢课是"Redis 裁决 + MySQL 提交点"，补偿可能也失败，
 * 会残留"Redis 有已选标记、DB 无记录"的差异 → 必须有个兜底定时拉平。
 *
 * fixedDelay = 上一次执行结束后再隔 5 分钟跑下一次（和 ShareJob 同款）。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CourseReconcileJob {

    private final CourseSeckillImpl courseSeckill;

    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void reconcile() {
        try {
            courseSeckill.reconcile();
        } catch (Exception e) {
            // 对账是兜底任务：自身抛错不能让线程死掉，记 ERROR 下次再跑
            log.error("抢课对账执行异常", e);
        }
    }
}