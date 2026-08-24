package com.scuplus.module.oss.dto;

import lombok.Data;

/**
 * 预签名上传响应
 *
 * presignedUrl：上传链接（前端拿它 PUT 文件到 MinIO，5 分钟内有效）
 * objectUrl：   访问链接（上传成功后存进帖子 mediaUrls，前端展示用）
 */
@Data
public class PresignDTO {

    /** 上传链接（前端 PUT 用） */
    private String presignedUrl;

    /** 访问链接（上传后存 mediaUrls） */
    private String objectUrl;
}