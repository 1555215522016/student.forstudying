package com.scuplus.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求：前端传来的学号和密码
 * @NotBlank 参数校验，失败时由 GlobalExceptionHandler 统一返回 BAD_REQUEST
 */
@Data
public class LoginRequest {

    @NotBlank(message = "学号不能为空")
    private String studentId;

    @NotBlank(message = "密码不能为空")
    private String password;
}
