package com.scuplus.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) 配置
 *
 * 启动后访问 http://localhost:8080/swagger-ui.html 查看接口文档，
 * 可以直接在页面里测试接口，替代 Postman 手工维护。
 *
 * 这里只自定义文档的标题/描述/版本，接口信息由 springdoc 自动扫描生成。
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI scuplusOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SCUPLUS 校园网站 API")
                        .description("SCUPLUS 后端接口文档（Spring Boot 3 + Session 认证）")
                        .version("v1.0.0"));
    }
}
