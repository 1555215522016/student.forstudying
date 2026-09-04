package com.scuplus.common.security;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 【压测专用】测试用户旁路：带 X-Test-UserId 头的请求，直接伪造登录态，绕过 session 认证。
 *
 * 为什么能绕过登录：
 *  抢课接口的鉴权只认 SecurityContext 里的 principal（LoginUser），不关心身份是"怎么来的"。
 *  正常链是 SessionAuthFilter 从 session 恢复身份；本过滤器在其之前，直接把请求头里的 userId
 *  构造成 LoginUser 塞进 SecurityContext —— 相当于直接往"身份通行证"盖章，
 *  抢课路径上一个 DB 认证查询都没有（压测只测抢课，不受登录污染）。
 *
 * 安全边界：
 *  1. 只由 SecurityConfig 在配置 test.user-bypass=true 时注册进过滤器链（生产 false → 根本不进链）
 *  2. 值必须是纯数字，防注入
 *  3. 不带该头的请求完全不受影响，照常走 session 认证
 *
 * 压测收尾：把配置 test.user-bypass 改回 false（或删掉该请求头）即可，代码可留可删。
 */
public class TestUserBypassFilter extends OncePerRequestFilter {

    public static final String HEADER_TEST_USER_ID = "X-Test-UserId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String testUserId = request.getHeader(HEADER_TEST_USER_ID);
        if (StrUtil.isNotBlank(testUserId) && StrUtil.isNumeric(testUserId)) {
            Long userId = Long.valueOf(testUserId);
            LoginUser loginUser = new LoginUser(userId, "test-" + userId, null, null, 0);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            loginUser, null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        }
        filterChain.doFilter(request, response);
    }
}