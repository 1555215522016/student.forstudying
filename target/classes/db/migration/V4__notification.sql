-- =====================================================================
-- V4: 通知模块（SSE 公告 + 点赞/评论提醒）
--    t_announcement : 公告正文（只有一份，全校共享）
--    t_notification : 每个用户各一行；is_read 已读/未读按人记
-- =====================================================================

CREATE TABLE IF NOT EXISTS t_announcement (
    id         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '公告ID',
    title      VARCHAR(128)  NOT NULL COMMENT '公告标题',
    content    VARCHAR(2048) NOT NULL COMMENT '公告正文',
    created_at DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告表';

CREATE TABLE IF NOT EXISTS t_notification (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '通知ID(单调递增，兼作Last-Event-ID)',
    user_id    BIGINT       NOT NULL COMMENT '接收者 user_id',
    type       TINYINT      NOT NULL COMMENT '通知类型：1公告 2点赞 3评论',
    source_id  BIGINT       NOT NULL DEFAULT 0 COMMENT '来源ID：公告id 或 帖子id',
    content    VARCHAR(512) NOT NULL DEFAULT '' COMMENT '展示摘要文本',
    is_read    TINYINT      NOT NULL DEFAULT 0 COMMENT '已读状态：0未读 1已读',
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '产生时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_user_unread (user_id, is_read, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户通知表';