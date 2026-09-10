package com.scuplus.module.user.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 个人详情（前端个人主页展示用）
 * 只暴露昵称/姓名/性别/生日/手机/专业/头像——其余敏感字段不外泄
 */
@Data
public class UserProfileVO {

    /** 用户 ID */
    private Long userId;

    /** 昵称 */
    private String nickname;

    /** 真实姓名 */
    private String name;

    /** 性别（已转成"男/女/未知"，前端直接展示，不输出 1/2） */
    private String gender;

    /** 生日 */
    private LocalDate birthday;

    /** 手机号 */
    private String phone;

    /** 专业 */
    private String major;

    /** 头像 */
    private String avatarUrl;
}