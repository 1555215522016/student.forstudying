package com.scuplus.module.share.controller;

import com.scuplus.common.exception.BusinessException;
import com.scuplus.common.exception.ErrorCode;
import com.scuplus.common.result.PageResult;
import com.scuplus.common.result.Result;
import com.scuplus.common.security.LoginUser;
import com.scuplus.module.share.dto.CommentCreateRequest;
import com.scuplus.module.share.dto.CommentVO;
import com.scuplus.module.share.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论接口（均需登录）
 *
 * POST /api/v1/posts/{postId}/comments   发评论
 * GET  /api/v1/posts/{postId}/comments   评论列表（分页）
 */
@RestController
@RequestMapping("/api/v1/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /** 发评论：登录用户，支持匿名 */
    @PostMapping
    public Result<Long> create(@PathVariable Long postId,
                               @Valid @RequestBody CommentCreateRequest request) {
        Long userId = currentUserId();
        return Result.success(commentService.create(postId, userId, request));
    }

    /** 评论列表：分页，时间正序 */
    @GetMapping
    public Result<PageResult<CommentVO>> list(@PathVariable Long postId,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return Result.success(commentService.listByPostId(postId, page, size));
    }

    /** 取当前登录用户 ID，未登录抛异常（评论模块全部需登录） */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getUserId();
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}