package com.scuplus.module.notify.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告：全校共享的唯一一份正文
 * 每用户的已读状态在 t_notification（sourceId 指向本表 id）
 */
@Data
@TableName("t_announcement")
public class Announcement {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 公告标题 */
    private String title;

    /** 公告正文 */
    private String content;

    /** 发布时间（DB 默认值） */
    private LocalDateTime createdAt;
}