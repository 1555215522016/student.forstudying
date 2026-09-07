package com.scuplus.module.notify.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户通知：公告/点赞/评论 — 每个用户一行
 * user_id/is_read 靠 map-underscore-to-camel-case 自动映射
 */
@Data
@TableName("t_notification")
public class Notification {

    /** 通知类型：公告 */
    public static final int TYPE_ANNOUNCEMENT = 1;
    /** 通知类型：点赞 */
    public static final int TYPE_LIKE = 2;
    /** 通知类型：评论 */
    public static final int TYPE_COMMENT = 3;

    /** 主键，数据库自增（单调递增，兼作 Last-Event-ID） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收者 user_id */
    private Long userId;

    /** 类型：1公告 2点赞 3评论 */
    private Integer type;

    /** 来源ID：公告id 或 帖子id */
    private Long sourceId;

    /** 展示摘要文本 */
    private String content;

    /** 已读：0未读 1已读 */
    private Integer isRead;

    /** 产生时间（DB 默认值） */
    private LocalDateTime createdAt;
}