package com.scuplus.module.share.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日常分享内容实体：对应 t_post 表
 *
 * mediaUrls 是 JSON 字段，实体里用 String 存 JSON 文本（如 ["/img/a.jpg"]），
 * 前端传数组、Service 层负责转成 JSON 字符串 —— 这是最简做法，不用配 typeHandler。
 */
@Data
@TableName("t_post")
public class Post {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发布者用户 ID */
    private Long userId;

    /** 文字内容 */
    private String content;

    /** 图片/视频地址（JSON 数组文本，如 ["/img/a.jpg","/video/b.mp4"]） */
    private String mediaUrls;

    /** 点赞数（延迟落库维护） */
    private Integer likeCount;

    /** 点踩数（延迟落库维护） */
    private Integer dislikeCount;

    /** 评论数（延迟落库维护） */
    private Integer commentCount;

    /** 状态：0正常 1已删除 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
