package com.scuplus.module.oss.service;

import com.scuplus.common.config.MinioConfig;
import com.scuplus.module.oss.dto.PresignDTO;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * OSS 服务：生成"预签名上传链接"，让前端直接上传文件到 MinIO
 *
 * 核心逻辑一句话：后端有 AccessKey（钥匙），用它生成一个【限时 5 分钟的 PUT 上传链接】。
 * 前端拿到链接直接 PUT 文件到 MinIO，文件不经过后端服务器。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssService {

    private final MinioClient minioClient;

    private final MinioConfig minioConfig;

    /** 启动时确保 bucket 存在（幂等），开发环境设为公开读，上传后前端可直接访问 */
    @PostConstruct
    public void init() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioConfig.getBucket()).build());
                log.info("MinIO bucket '{}' 已创建", minioConfig.getBucket());
            }
            if (minioConfig.getPublicRead()) {
                String policy = """
                        {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}
                        """.formatted(minioConfig.getBucket());
                minioClient.setBucketPolicy(
                        SetBucketPolicyArgs.builder().bucket(minioConfig.getBucket()).config(policy).build());
                log.info("MinIO bucket '{}' 已设为公开读", minioConfig.getBucket());
            }
        } catch (Exception e) {
            // MinIO 未启动时不让应用启动失败，仅记日志（容错）
            log.error("MinIO bucket 初始化失败，请确认 MinIO 已启动", e);
        }
    }

    /**
     * 生成预签名上传链接
     *
     * @param objectName 存储路径（如 posts/1/abc123.jpg），由 Controller 生成
     * @return presignedUrl（PUT 用）+ objectUrl（访问用）
     */
    public PresignDTO presign(String objectName) {
        try {
            // 生成 PUT 预签名链接：前端用它直接上传，5 分钟内有效
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioConfig.getBucket())
                            .object(objectName)
                            .expiry(5, TimeUnit.MINUTES)
                            .build());

            // 生成 GET 预签名链接：前端展示用，7 天内有效
            String objectUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioConfig.getBucket())
                            .object(objectName)
                            .expiry(7, TimeUnit.DAYS)
                            .build());

            PresignDTO dto = new PresignDTO();
            dto.setPresignedUrl(presignedUrl);
            dto.setObjectUrl(objectUrl);
            return dto;
        } catch (Exception e) {
            throw new RuntimeException("生成上传链接失败", e);
        }
    }
}