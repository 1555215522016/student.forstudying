package com.scuplus.module.share.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 发帖请求：前端传来的数据
 */
@Data
public class PostCreateRequest {

    /** 内容文字（必填） */
    @NotBlank(message = "内容不能为空")
    private String content;

    /** 图片/视频地址数组 */
    private List<String> mediaUrls;

    /** 是否匿名：true=匿名 */
    private Boolean isAnonymous;
}
