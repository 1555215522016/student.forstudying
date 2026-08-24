package com.scuplus.common.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录用户信息：登录成功后存入 HttpSession
 *
 * 后续每个请求，SessionAuthFilter 从 session 取出它，
 * 作为 Spring Security 认证信息的 principal（认证主体）。
 *
 * 注意：这是"登录态快照"，不是数据库的 User 实体。
 * 网站登录方式：学号 + 学校统一认证密码（Demo 阶段用模拟认证）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    /** 用户 ID（t_user 表主键，业务关联用） */
    private Long userId;

    /** 学号 */
    private String studentId;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatarUrl;

    /** 角色：0普通用户 1管理员 */
    private Integer role;
}
