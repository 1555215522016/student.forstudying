package com.scuplus.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scuplus.common.exception.ErrorCode;
import com.scuplus.common.result.Result;
import com.scuplus.common.security.SessionAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置（Session + Cookie 方案）
 *
 * Security 在这里扮演"门卫"而不是"认证主力"：
 *   登录和会话由业务自己管理（登录成功 → 手动塞 session），
 *   Security 只负责——哪些门要检查、检查不通过怎么返回。
 *
 * 三个设计决策：
 * 1. 关闭 CSRF：前后端分离 API + SameSite Cookie，关闭更干净
 * 2. Session 策略 IF_REQUIRED：登录了才建 session，匿名请求不建（省内存）
 * 3. 未登录返回统一 JSON：替换 Security 默认的 403/登录页，与前端 Result 约定对齐
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 免登录路径（白名单） */
    private static final String[] WHITE_LIST = {
            "/api/v1/auth/login",   // 登录接口（未登录可访问；me/logout 需要登录）
            "/api/v1/notices",      // 首页公告（公开）
            "/api/v1/notices/**",
            "/swagger-ui.html",     // Swagger 文档
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/error"
    };

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 启用 CORS（配合 CorsConfig 里的配置）
                .cors(Customizer.withDefaults())
                // 前后端分离 API，关闭 CSRF
                .csrf(csrf -> csrf.disable())
                // 有状态会话：仅登录后创建
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // 白名单放行，其余要求认证
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(WHITE_LIST).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                // 未登录访问受限资源 → 返回统一 JSON {status:40100}
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    objectMapper.writeValueAsString(Result.error(ErrorCode.UNAUTHORIZED)));
                        }))
                // 我们的 SessionAuthFilter 在认证过滤器之前执行，先恢复会话认证
                .addFilterBefore(new SessionAuthFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
