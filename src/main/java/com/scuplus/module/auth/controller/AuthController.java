package com.scuplus.module.auth.controller;

import com.scuplus.common.result.Result;
import com.scuplus.common.security.LoginUser;
import com.scuplus.common.security.SessionAuthFilter;
import com.scuplus.module.auth.dto.LoginRequest;
import com.scuplus.module.auth.dto.LoginResponse;
import com.scuplus.module.auth.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口
 *
 * 登录成功后的关键动作：把 LoginUser 塞进 session。
 * 这就是"发身份卡"——后续请求 SessionAuthFilter 从 session 认出用户。
 * 注意：登录接口放行（白名单），登出/当前用户需要登录。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 登录：模拟认证 → 建档/查用户 → 塞 session → 返回用户信息 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpSession session) {
        LoginUser loginUser = authService.login(request.getStudentId(), request.getPassword());
        // 发身份卡：把登录用户存进 session（key 和 SessionAuthFilter 里一致）
        session.setAttribute(SessionAuthFilter.SESSION_KEY, loginUser);
        return Result.success(toResponse(loginUser));
    }

    /** 登出：销毁 session = 服务端踢人 */
    @PostMapping("/logout")
    public Result<Void> logout(HttpSession session) {
        session.invalidate();
        return Result.success();
    }

    /** 当前登录用户：从 SecurityContext 拿（SessionAuthFilter 已恢复） */
    @GetMapping("/me")
    public Result<LoginResponse> me() {
        LoginUser loginUser = authService.getCurrentUser();
        return Result.success(toResponse(loginUser));
    }

    /** LoginUser → LoginResponse（只暴露需要给前端的字段） */
    private LoginResponse toResponse(LoginUser loginUser) {
        LoginResponse resp = new LoginResponse();
        resp.setUserId(loginUser.getUserId());
        resp.setStudentId(loginUser.getStudentId());
        resp.setNickname(loginUser.getNickname());
        resp.setAvatarUrl(loginUser.getAvatarUrl());
        return resp;
    }
}
