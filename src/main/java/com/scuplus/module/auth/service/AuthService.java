package com.scuplus.module.auth.service;

import com.scuplus.common.security.LoginUser;

/**
 * 认证服务
 */
public interface AuthService {

    /**
     * 登录认证：模拟学校统一认证，通过后返回用户
     * 用户不存在时自动建档（首次登录即注册）
     */
    LoginUser login(String studentId, String password);

    /** 获取当前登录用户（从 SecurityContext 取） */
    LoginUser getCurrentUser();
}
