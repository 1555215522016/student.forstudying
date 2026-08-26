package com.scuplus.module.oss.dto;

import lombok.Data;

/**
 * 申请分片预签名 URL 的请求
 */
@Data
public class PresignPartsRequest {

    /** 分片上传会话 ID */
    private String uploadId;

    /** 总共几片（前端按 chunkSize 切分后决定） */
    private Integer total;
}