package com.scuplus.module.share.service;

import com.scuplus.common.result.PageResult;
import com.scuplus.module.share.dto.CommentCreateRequest;
import com.scuplus.module.share.dto.CommentVO;

/**
 * 评论服务
 */
public interface CommentService {

    /**
     * 发评论（需登录）
     * @param postId 被评论的帖子
     * @param userId 评论者
     * @param req    评论内容（含是否匿名）
     * @return 评论 ID
     */
    Long create(Long postId, Long userId, CommentCreateRequest req);

    /**
     * 帖子评论列表（分页，按时间正序）
     * @param postId 帖子
     * @param page 页码
     * @param size 每页条数
     */
    PageResult<CommentVO> listByPostId(Long postId, int page, int size);
}