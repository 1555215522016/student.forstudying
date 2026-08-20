package com.scuplus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SCUPLUS 后端启动类
 *
 * @SpringBootApplication = @SpringBootConfiguration + @EnableAutoConfiguration + @ComponentScan
 * 启动后自动扫描 com.scuplus 包下所有组件（Controller/Service/Mapper 等），
 * 并自动装配项目里引入的依赖（数据源、Redis、MyBatis-Plus 等）。
 */
@SpringBootApplication
public class ScuplusApplication {

    public static void main(String[] args) {SpringApplication.run(ScuplusApplication.class, args);
    }
}
