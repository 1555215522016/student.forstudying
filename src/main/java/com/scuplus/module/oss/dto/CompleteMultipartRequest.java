package com.scuplus.module.oss.dto;

import lombok.Data;

import java.util.List;

/**
 * 合并分片请求：前端把所有已传分片的 partNumber + etag 一起发来
 */
@Data
public class CompleteMultipartRequest {

    /** 分片上传会话 ID */
    private String uploadId;

    /** 所有已传分片（按 partNumber 从小到大） */
    private List<PartDTO> parts;
}