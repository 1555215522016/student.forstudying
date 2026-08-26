package com.scuplus.module.oss.dto;

import lombok.Data;

/**
 * 取消分片上传请求
 */
@Data
public class AbortRequest {

    /** 分片上传会话 ID */
    private String uploadId;
}