package com.scuplus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SCUPLUS 后端启动类
 *
 * @SpringBootApplication = @SpringBootConfiguration + @EnableAutoConfiguration + @ComponentScan
 * @EnableScheduling = 开启定时任务功能（配合 @Scheduled 注解，如点赞数延迟落库）
 */
@SpringBootApplication
@EnableScheduling
public class ScuplusApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScuplusApplication.class, args);
    }
}
