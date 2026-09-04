-- =====================================================
-- V2__daily_share.sql   SCUPLUS 日常分享模块
--
-- 三张表：t_post(内容) / t_comment(评论) / t_like(点赞点踩)
-- =====================================================

-- 内容表：一条分享（文字 + 图片/视频 + 计数）
-- 计数 like_count/dislike_count/comment_count 是"冗余派生字段"，
-- 由 Redis 计数 + 定时落库维护（接受短时不一致，换列表查询速度）
CREATE TABLE IF NOT EXISTS t_post (
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id        BIGINT      NOT NULL COMMENT '发布者用户ID',
    content        TEXT        COMMENT '文字内容',
    is_anonymous   TINYINT     NOT NULL DEFAULT 0 COMMENT '是否匿名：0否 1是',
    media_urls     JSON        COMMENT '图片/视频地址数组，如 ["/img/a.jpg","/video/b.mp4"]',
    like_count     INT         NOT NULL DEFAULT 0 COMMENT '点赞数（延迟落库维护）',
    dislike_count  INT         NOT NULL DEFAULT 0 COMMENT '点踩数（延迟落库维护）',
    comment_count  INT         NOT NULL DEFAULT 0 COMMENT '评论数（延迟落库维护）',
    status         TINYINT     NOT NULL DEFAULT 0 COMMENT '状态：0正常 1已删除',
    created_at     DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日常分享内容表';

-- 评论表：只做一层评论（不做嵌套楼层）
CREATE TABLE IF NOT EXISTS t_comment (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    post_id     BIGINT       NOT NULL COMMENT '所属内容ID',
    user_id     BIGINT       NOT NULL COMMENT '评论者用户ID',
    content     VARCHAR(500) NOT NULL COMMENT '评论内容',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0正常 1已删除',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_post_id (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容评论表';

-- 点赞/点踩表：存"事实"（谁对哪篇点了什么）
-- 唯一索引 (post_id, user_id)：同一个人对同一篇只能有一条，数据库层防重复
-- type 切换用 UPDATE（1赞 2踩），不保留历史
CREATE TABLE IF NOT EXISTS t_like (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    post_id     BIGINT   NOT NULL COMMENT '内容ID',
    user_id     BIGINT   NOT NULL COMMENT '用户ID',
    type        TINYINT  NOT NULL DEFAULT 1 COMMENT '类型：1点赞 2点踩',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_user (post_id, user_id),
    KEY idx_post_id (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点赞/点踩表';
