package com.scuplus.module.share.controller;

import com.scuplus.common.exception.BusinessException;
import com.scuplus.common.exception.ErrorCode;
import com.scuplus.common.result.PageResult;
import com.scuplus.common.result.Result;
import com.scuplus.common.security.LoginUser;
import com.scuplus.module.share.dto.PostCreateRequest;
import com.scuplus.module.share.dto.PostVO;
import com.scuplus.module.share.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 帖子接口
 *
 * POST /api/v1/posts          发帖（需要登录）
 * GET  /api/v1/posts          列表（不需要登录，公开访问）
 * GET  /api/v1/posts/{postId} 详情（不需要登录）
 */
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /** 发帖：登录后可以发，支持匿名 */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody PostCreateRequest request) {
        Long userId = currentUserId();
        if (userId == null) {
            throw new com.scuplus.common.exception.BusinessException(
                    com.scuplus.common.exception.ErrorCode.UNAUTHORIZED);
        }
        return Result.success(postService.create(userId, request));
    }

    /** 列表：公开访问，分页 */
    @GetMapping
    public Result<PageResult<PostVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(postService.list(page, size));
    }

    /** 详情：公开访问，返回全部字段 */
    @GetMapping("/{postId}")
    public Result<PostVO> detail(@PathVariable Long postId) {
        Long userId = currentUserId();
        if(userId==null){
            throw new  BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return Result.success(postService.detail(postId, userId));
    }

    /** 删除帖子：本人 或 管理员 */
    @DeleteMapping("/{postId}")
    public Result<Void> delete(@PathVariable Long postId) {
        LoginUser user = currentLoginUser();
        postService.delete(postId, user.getUserId(), user.getRole());
        return Result.success();
    }

    /** 取当前登录用户完整信息（含角色），未登录抛异常 */
    private LoginUser currentLoginUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    /** 取当前用户 ID，未登录返回 null（不抛异常） */
    private Long currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getUserId();
        }
        return null;
    }

    /** 取当前用户 ID，未登录抛异常 */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getUserId();
        }
        throw new com.scuplus.common.exception.BusinessException(
                com.scuplus.common.exception.ErrorCode.UNAUTHORIZED);
    }
}
