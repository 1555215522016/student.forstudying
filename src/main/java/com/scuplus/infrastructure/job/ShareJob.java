package com.scuplus.infrastructure.job;

import com.scuplus.module.share.entity.Post;
import com.scuplus.module.share.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 分享模块定时任务
 *
 * 功能：把 Redis 里的点赞数/点踩数定期写回 MySQL 存档（延迟落库）
 *
 * 为什么需要？
 *  Redis 是内存，会丢数据。点赞数平时读 Redis（秒回），但要定期把最终数字
 *  写回 t_post.like_count / dislike_count，Redis 挂了也有 MySQL 兜底恢复。
 *
 * 为什么叫"延迟"？
 *  定时任务每 30 秒跑一次，所以 MySQL 里的数字最多比 Redis 滞后 30 秒。
 *  这是"最终一致"——不要求每一秒都准，但最终会一致。
 */
@Component
@RequiredArgsConstructor
public class ShareJob {

    private final PostMapper postMapper;
    private final StringRedisTemplate redis;

    private static final String KEY_LIKES = "post:%d:likes";
    private static final String KEY_DISLIKES = "post:%d:dislikes";

    /**
     * 每 30 秒把 Redis 的点赞/点踩数同步到 MySQL
     * fixedDelay = 上一次执行结束后 3 万毫秒（30秒）再执行下一次
     */
    @Scheduled(fixedDelay = 30000)
    public void syncLikeCounts() {
        List<Post> posts = postMapper.selectList(null);
        for (Post post : posts) {
            // Redis Set 的 size = 点赞人数（key 不存在时 size 返回 0）
            int likeCount = redis.opsForSet().size(KEY_LIKES.formatted(post.getId())).intValue();
            int dislikeCount = redis.opsForSet().size(KEY_DISLIKES.formatted(post.getId())).intValue();

            post.setLikeCount(likeCount);
            post.setDislikeCount(dislikeCount);
            postMapper.updateById(post);
        }
    }
}