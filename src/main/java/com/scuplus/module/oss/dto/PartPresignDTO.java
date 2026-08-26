package com.scuplus.module.oss.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个分片的预签名上传信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartPresignDTO {

    /** 分片序号 */
    private Integer partNumber;

    /** 该分片的预签名 PUT URL（前端直接 PUT 原始字节到 MinIO） */
    private String presignedUrl;
}