-- =====================================================
-- V1__init_schema.sql   SCUPLUS 初始表结构
--
-- 建表顺序：先 t_user，再 t_user_credential（外键依赖）
-- 执行方式：见 CLAUDE.md（手动执行进 Docker 的 MySQL）
-- =====================================================

-- 用户表：身份 + 资料
-- 注意：不存"网站登录密码"——认证委托学校系统（Demo 用模拟认证）。
--       密码字段只出现在 t_user_credential（爬取凭证，AES 加密）。
-- 注意：存"事实"不存"派生字段"——用 birthday 推年龄、enroll_year 推大几。
CREATE TABLE IF NOT EXISTS t_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    student_id  VARCHAR(32)  NOT NULL COMMENT '学号',
    name        VARCHAR(64)  DEFAULT NULL COMMENT '姓名',
    gender      TINYINT      DEFAULT 0 COMMENT '性别：0未知 1男 2女',
    birthday    DATE         DEFAULT NULL COMMENT '出生日期',
    enroll_year INT          DEFAULT NULL COMMENT '入学年份',
    status      TINYINT      DEFAULT 0 COMMENT '当前状态：0在校 1毕业 2停学',
    nickname    VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    avatar_url  VARCHAR(256) DEFAULT NULL COMMENT '头像URL',
    major       VARCHAR(64)  DEFAULT NULL COMMENT '专业',
    phone       VARCHAR(32)  DEFAULT NULL COMMENT '手机号',
    email       VARCHAR(64)  DEFAULT NULL COMMENT '邮箱',
    role        TINYINT      NOT NULL DEFAULT 0 COMMENT '角色：0普通 1管理员',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_id (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 用户外部系统凭证表：存爬取学校系统所需的账号密码（AES 加密）
-- 为什么单独一张：和 t_user 是 1:N（一个用户绑教务/图书馆等多个系统）、
--               生命周期不同（可解绑重绑）、敏感度最高（密码）。
CREATE TABLE IF NOT EXISTS t_user_credential (
    id                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id            BIGINT        NOT NULL COMMENT '用户ID（关联 t_user.id）',
    type               VARCHAR(16)   NOT NULL COMMENT '凭证类型：jwc=教务 library=图书馆',
    account            VARCHAR(64)   DEFAULT NULL COMMENT '外部系统账号（学号）',
    encrypted_password VARCHAR(256)  DEFAULT NULL COMMENT 'AES加密后的密码',
    created_at         DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    UNIQUE KEY uk_user_type (user_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户外部系统凭证表';
