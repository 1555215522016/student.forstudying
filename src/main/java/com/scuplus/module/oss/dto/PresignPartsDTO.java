package com.scuplus.module.oss.dto;

import lombok.Data;

import java.util.List;

/**
 * 分片预签名 URL 列表（前端拿到后直接 PUT 每片到 MinIO）
 */
@Data
public class PresignPartsDTO {

    /** 分片会话 ID */
    private String uploadId;

    /** 每个分片的预签名 PUT URL */
    private List<PartPresignDTO> parts;
}