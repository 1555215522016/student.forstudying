package com.scuplus.module.user.dto;

import lombok.Data;

/**
 * 个人资料修改请求：只允许改 昵称 / 手机号 / 头像，其余字段（姓名/性别/生日/专业）只读
 * 字段可空：null=不改；空字符串=清空
 */
@Data
public class ProfileUpdateRequest {

    /** 昵称（null=不改） */
    private String nickname;

    /** 手机号（null=不改，空串=清空） */
    private String phone;

    /** 头像 URL（null=不改） */
    private String avatarUrl;
}