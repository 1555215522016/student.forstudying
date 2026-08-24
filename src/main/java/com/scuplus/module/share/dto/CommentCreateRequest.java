package com.scuplus.module.share.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentCreateRequest {
    @Size(max = 100,message = "评论不能超过100字")
    @NotBlank(message = "字数不得为空")
    private String content;
    private Boolean isAnonymous;

}
