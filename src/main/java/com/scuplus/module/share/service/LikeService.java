package com.scuplus.module.share.service;

/**
 * 点赞/点踩服务
 */
public interface LikeService {

    /**
     * 对某篇内容"点一下赞"
     * 未点赞 → 点赞；已点赞 → 取消赞（切换为无状态）
     * @param postId 内容ID
     * @param userId 用户ID
     * @return 操作后的点赞数
     */
    int like(Long postId, Long userId);

    /**
     * 对某篇内容"点一下踩"
     * 未点踩 → 点踩；已点踩 → 取消踩
     * @param postId 内容ID
     * @param userId 用户ID
     * @return 操作后的点踩数
     */
    int dislike(Long postId, Long userId);
}
