package com.scuplus.module.notify.controller;

import com.scuplus.common.exception.BusinessException;
import com.scuplus.common.exception.ErrorCode;
import com.scuplus.common.result.Result;
import com.scuplus.common.security.LoginUser;
import com.scuplus.module.notify.dto.AnnouncementCreateRequest;
import com.scuplus.module.notify.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公告接口：仅管理员(role=1)可发布
 * 发布后给全校每个用户落一条通知，在线者实时推，离线者下次连上补发
 */
@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final NotificationService notificationService;

    @PostMapping
    public Result<Void> publish(@Valid @RequestBody AnnouncementCreateRequest request) {
        LoginUser user = currentLoginUser();
        if (user.getRole() == null || user.getRole() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可发布公告");
        }
        notificationService.publishAnnouncement(request.getTitle(), request.getContent());
        return Result.success();
    }

    private LoginUser currentLoginUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}