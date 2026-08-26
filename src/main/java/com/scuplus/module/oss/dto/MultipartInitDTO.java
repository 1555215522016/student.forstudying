package com.scuplus.module.oss.dto;

import lombok.Data;

/**
 * 分片上传初始化响应
 */
@Data
public class MultipartInitDTO {

    /** 分片上传会话 ID（后续所有分片操作都要带它） */
    private String uploadId;

    /** 对象存储路径（如 videos/1/xxx.mp4） */
    private String objectName;

    /** 建议分片大小（字节），前端按此切分（≥5MB，遵守 S3 限制） */
    private Long chunkSize;
}