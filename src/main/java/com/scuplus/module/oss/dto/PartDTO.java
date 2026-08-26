package com.scuplus.module.oss.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分片信息：partNumber（第几片）+ etag（该片的校验标记）
 * 前端逐片上传后收集所有 Part，最后传给 complete 合并
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartDTO {

    /** 分片序号（从 1 开始） */
    private Integer partNumber;

    /** 该分片的 etag（MinIO 返回，合并时校验用） */
    private String etag;
}