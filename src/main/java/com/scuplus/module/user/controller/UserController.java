package com.scuplus.module.user.controller;

import com.scuplus.common.exception.BusinessException;
import com.scuplus.common.exception.ErrorCode;
import com.scuplus.common.result.Result;
import com.scuplus.common.security.LoginUser;
import com.scuplus.module.user.dto.ProfileUpdateRequest;
import com.scuplus.module.user.dto.UserProfileVO;
import com.scuplus.module.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户个人详情
 *
 * GET  /api/v1/users/{userId}/profile   查看任意用户的详情（需登录）
 * PUT  /api/v1/users/{userId}/profile   修改自己的 昵称/手机号/头像（只能改自己）
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;

    @GetMapping("/{userId}/profile")
    public Result<UserProfileVO> profile(@PathVariable Long userId) {
        currentUserId(); // 需登录才能看
        return Result.success(userProfileService.getProfile(userId));
    }

    @PutMapping("/{userId}/profile")
    public Result<Void> updateProfile(@PathVariable Long userId,
                                      @RequestBody ProfileUpdateRequest request) {
        Long me = currentUserId();
        if (!me.equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能修改自己的资料");
        }
        userProfileService.updateProfile(userId, request);
        // 同步刷新"登录态快照"：/me 和顶栏头像/昵称立即生效，不用重新登录
        // session 里存的就是这个 LoginUser 对象（SessionAuthFilter 用它当 principal），直接改即可
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            if (request.getNickname() != null) {
                loginUser.setNickname(request.getNickname().trim());
            }
            if (request.getAvatarUrl() != null) {
                loginUser.setAvatarUrl(request.getAvatarUrl());
            }
        }
        return Result.success();
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getUserId();
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}