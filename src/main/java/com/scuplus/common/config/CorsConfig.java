package com.scuplus.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS 跨域配置
 *
 * 场景：前端网站（如 localhost:3000）访问后端（localhost:8080），
 * 两者不同源，浏览器会拦截跨域请求，需要后端声明"允许谁跨域"。
 *
 * 关键点：必须 setAllowCredentials(true)——Session 方案靠 Cookie(JSESSIONID)
 * 维持登录态，跨域请求要带 Cookie 必须允许携带凭证。
 * 注意：allowCredentials(true) 时不能配 origin="*"，要用 allowedOriginPattern("*")。
 *
 * SecurityConfig 里已 .cors(withDefaults())，会找到这里的 Bean 使用。
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许携带 Cookie（Session 登录态跨域的关键）
        config.setAllowCredentials(true);
        // 允许的源：开发环境放开，生产环境应改成具体域名白名单
        config.addAllowedOriginPattern("*");
        // 允许的方法和请求头
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        // 预检请求(OPTIONS)结果缓存 1 小时，减少不必要的预检
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
