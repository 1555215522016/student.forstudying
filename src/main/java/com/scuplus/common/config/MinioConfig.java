package com.scuplus.common.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 连接配置
 * 生成一个 MinioClient Bean = "一把连好 MinIO 的大门"，供 OssService 使用
 */
@Configuration
@Data
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    /*@Value("${scuplus.minio.endpoint}")*/
    private String endpoint;

    /*@Value("${scuplus.minio.access-key}")*/
    private String accessKey;

    /*@Value("${scuplus.minio.secret-key}")*/
    private String secretKey;

    private String bucket;

    private Boolean publicRead;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}