package com.scuplus.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Session 认证过滤器：从 HttpSession 里捞登录用户，塞进 SecurityContext
 *
 * 为什么继承 OncePerRequestFilter？
 * 保证一次请求只执行一次过滤，避免被过滤器链重复调用。
 *
 * 流程：
 *   1. request.getSession(false) 拿已有 session（false=不新建，
 *      避免给每个匿名请求都创建 session 浪费内存）
 *   2. session 里有 loginUser → 构造 Authentication 放进 SecurityContextHolder
 *   3. 没有 → 不设置认证，放行，让 Security 自己判断该路径要不要拦截
 *
 * SecurityContextHolder 里有没有认证信息，就是"这个请求是不是登录用户"的判断依据。
 */
public class SessionAuthFilter extends OncePerRequestFilter {

    /** session 里存登录用户信息的 key（登录模块写入，这里读取） */
    public static final String SESSION_KEY = "loginUser";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session != null) {
            LoginUser loginUser = (LoginUser) session.getAttribute(SESSION_KEY);
            if (loginUser != null) {
                // 构造认证信息：principal=用户对象，凭证=null（会话已认证，不再需要凭证）
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                loginUser, null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}
