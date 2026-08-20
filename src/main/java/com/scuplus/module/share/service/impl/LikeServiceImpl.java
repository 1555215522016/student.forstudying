package com.scuplus.module.share.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scuplus.common.exception.BusinessException;
import com.scuplus.common.exception.ErrorCode;
import com.scuplus.module.share.entity.Like;
import com.scuplus.module.share.entity.Post;
import com.scuplus.module.share.mapper.LikeMapper;
import com.scuplus.module.share.mapper.PostMapper;
import com.scuplus.module.share.service.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 点赞/点踩服务实现
 *
 * 一致性设计（三条存储路径各司其职）：
 *  - MySQL t_like = 权威真相（判断谁点过、存 type，即使 Redis 丢数据也正确）
 *  - Redis Set = 加速读（浏览时 SCARD 秒回点赞数；SISMEMBER 查个人状态）
 *
 * 点赞/点踩切换：用户从"赞"切到"踩"，只 UPDATE type（不保留历史）
 * 并发双击：捕获唯一键冲突(DuplicateKeyException) → 幂等返回，不报 500
 */
@Service
@RequiredArgsConstructor
public class LikeServiceImpl implements LikeService {

    private final LikeMapper likeMapper;
    private final PostMapper postMapper;
    private final StringRedisTemplate redis;

    /** Redis：某篇内容的点赞用户集合 */
    private static final String KEY_LIKES = "post:%d:likes";
    /** Redis：某篇内容的点踩用户集合 */
    private static final String KEY_DISLIKES = "post:%d:dislikes";

    /** t_like.type：点赞 */
    private static final int TYPE_LIKE = 1;
    /** t_like.type：点踩 */
    private static final int TYPE_DISLIKE = 2;

    @Override
    public int like(Long postId, Long userId) {
        return toggle(postId, userId, TYPE_LIKE, KEY_LIKES, KEY_DISLIKES);
    }

    @Override
    public int dislike(Long postId, Long userId) {
        return toggle(postId, userId, TYPE_DISLIKE, KEY_DISLIKES, KEY_LIKES);
    }

    /**
     * 通用的"点一下"逻辑
     *
     * @param postId    内容
     * @param userId    用户
     * @param targetType 本次操作的类型（赞或踩）
     * @param targetKey  目标类型对应的 Redis 集合
     * @param oppositeKey 相反类型对应的 Redis 集合（用于切换）
     */
    private int toggle(Long postId, Long userId, int targetType,
                       String targetKey, String oppositeKey) {
        // 0. 校验内容存在
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "内容不存在");
        }

        // 1. 查 MySQL（权威）：这个用户对这篇的现有记录
        Like existing = likeMapper.selectOne(new LambdaQueryWrapper<Like>()
                .eq(Like::getPostId, postId)
                .eq(Like::getUserId, userId));

        try {
            if (existing == null) {
                // 场景1：从未操作过 → 新增一条目标类型记录
                Like like = new Like();
                like.setPostId(postId);
                like.setUserId(userId);
                like.setType(targetType);
                likeMapper.insert(like);
                // Redis：加入目标集合，同时从相反集合移除（防御脏数据）
                redis.opsForSet().add(formatKey(targetKey, postId), userId.toString());
                redis.opsForSet().remove(formatKey(oppositeKey, postId), userId.toString());
            } else if (existing.getType() == targetType) {
                // 场景2：已点过同类 → 取消（删除记录）
                likeMapper.deleteById(existing.getId());
                redis.opsForSet().remove(formatKey(targetKey, postId), userId.toString());
            } else {
                // 场景3：点过相反类型 → 切换 type（UPDATE）
                existing.setType(targetType);
                likeMapper.updateById(existing);
                // Redis：从相反集合移除，加入目标集合
                redis.opsForSet().remove(formatKey(oppositeKey, postId), userId.toString());
                redis.opsForSet().add(formatKey(targetKey, postId), userId.toString());
            }
        } catch (DuplicateKeyException e) {
            // 并发双击：唯一键冲突被数据库拦截，幂等返回当前点赞数，不报错
            return targetType == TYPE_LIKE
                    ? redis.opsForSet().size(KEY_LIKES).intValue()
                    : redis.opsForSet().size(KEY_DISLIKES).intValue();
        }

        // 返回该类型集合的最新大小（点赞数/点踩数）
        return redis.opsForSet().size(formatKey(targetKey, postId)).intValue();
    }

    /** 把 "post:%d:likes" 模板 + postId 拼成实际 key */
    private String formatKey(String template, long postId) {
        return template.formatted(postId);
    }
}
