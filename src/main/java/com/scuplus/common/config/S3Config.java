package com.scuplus.common.config;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AWS S3 客户端配置（连接 MinIO 用）
 *
 * 为什么需要有它：MinIO 完全兼容 S3 协议，但 MinIO 自己的 SDK 不暴露"手动分片"接口。
 * 分片上传（逐片传 / 断点续传）需要 S3 标准 multipart API，所以用 AmazonS3 连 MinIO。
 *
 * 三个关键配置：
 *  - endpointOverride：指向 MinIO（http://localhost:9000）
 *  - pathStyle(true)：用 path 式访问（http://localhost:9000/bucket/object），
 *      MinIO 本地不支持 S3 默认的 virtual-host 式访问，必须开这个
 *  - region 任意值：MinIO 会忽略 region
 */
@Configuration
@RequiredArgsConstructor
public class S3Config {

    private final MinioConfig minioConfig;

    @Bean
    public AmazonS3 s3Client() {
        return AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        minioConfig.getEndpoint(), "us-east-1"))
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(
                                minioConfig.getAccessKey(), minioConfig.getSecretKey())))
                .withPathStyleAccessEnabled(true)
                .build();
    }
}