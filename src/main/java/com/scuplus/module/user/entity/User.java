package com.scuplus.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户实体：对应 t_user 表
 *
 * 三个关键注解：
 * 1. @TableName("t_user") —— 告诉 MyBatis-Plus 这个类对应哪张表
 * 2. @TableId —— 标记主键字段
 * 3. @Data —— Lombok 生成 getter/setter
 *
 * 字段不需要 @TableField 标列名，靠 application.yml 里的
 * map-underscore-to-camel-case 自动映射（student_id ↔ studentId）
 */
@Data
@TableName("t_user")
public class User {

    /** 主键，数据库自增（对应全局配置 id-type: auto） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学号（登录标识） */
    private String studentId;

    /** 姓名 */
    private String name;

    /** 性别：0未知 1男 2女 */
    private Integer gender;

    /** 出生日期（DATE 类型 → LocalDate） */
    private LocalDate birthday;

    /** 入学年份（大几由它推导） */
    private Integer enrollYear;

    /** 当前状态：0在校 1毕业 2停学 */
    private Integer status;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatarUrl;

    /** 专业 */
    private String major;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 角色：0普通用户 1管理员 */
    private Integer role;

    /** 创建时间（DATETIME → LocalDateTime） */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
