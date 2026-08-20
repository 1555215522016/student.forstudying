package com.scuplus.module.auth.dto;

import lombok.Data;

/**
 * 登录响应：返回给前端的用户信息
 * 注意：只暴露需要给前端的字段，不直接返回 User 实体（避免泄露内部字段）
 */
@Data
public class LoginResponse {

    /** 用户 ID */
    private Long userId;

    /** 学号 */
    private String studentId;

    /** 昵称 */
    private String nickname;

    /** 头像 */
    private String avatarUrl;
}
