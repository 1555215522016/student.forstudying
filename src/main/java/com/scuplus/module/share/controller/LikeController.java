package com.scuplus.module.share.controller;

import com.scuplus.common.result.Result;
import com.scuplus.common.security.LoginUser;
import com.scuplus.module.share.service.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点赞/点踩接口
 * 赞和踩拆成两个接口，避免用 type 参数（语义更清晰）
 * 需要登录（SessionAuthFilter 已从 session 恢复认证，SecurityConfig 白名单外会挡）
 */
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    /** 点赞：未赞→赞；已赞→取消赞。返回最新点赞数 */
    @PostMapping("/{postId}/like")
    public Result<Integer> like(@PathVariable Long postId) {
        return Result.success(likeService.like(postId, currentUserId()));
    }

    /** 点踩：未踩→踩；已踩→取消踩。返回最新点踩数 */
    @PostMapping("/{postId}/dislike")
    public Result<Integer> dislike(@PathVariable Long postId) {
        return Result.success(likeService.dislike(postId, currentUserId()));
    }

    /** 从 SecurityContext 取当前登录用户 ID（SessionAuthFilter 已放入 LoginUser） */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getUserId();
        }
        throw new com.scuplus.common.exception.BusinessException(
                com.scuplus.common.exception.ErrorCode.UNAUTHORIZED);
    }
}
