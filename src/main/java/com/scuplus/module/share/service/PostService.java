package com.scuplus.module.share.service;

import com.scuplus.common.result.PageResult;
import com.scuplus.module.share.dto.PostCreateRequest;
import com.scuplus.module.share.dto.PostVO;

import java.util.List;

/**
 * 帖子服务
 */
public interface PostService {

    /** 发布帖子，返回帖子 ID */
    Long create(Long userId, PostCreateRequest request);

    /** 列表（分页），返回摘要（不含 mediaUrls） */
    PageResult<PostVO> list(int page, int size);

    /** 详情，返回全部字段（含 mediaUrls + 点赞状态） */
    PostVO detail(Long postId, Long currentUserId);
}
