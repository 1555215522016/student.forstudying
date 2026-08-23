package com.scuplus.module.share.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日常分享内容实体：对应 t_post 表
 *
 * mediaUrls 使用 MySQL JSON 类型 + JacksonTypeHandler，
 * Java 里用 List<String> 操作，MyBatis-Plus 自动序列化/反序列化。
 */
@Data
@TableName(value = "t_post", autoResultMap = true)
public class Post {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发布者用户 ID（始终存真实 ID，匿名处理在 Service 层） */
    private Long userId;

    /** 文字内容 */
    private String content;

    /** 图片/视频地址（JSON 数组，MySQL JSON 类型自动处理） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> mediaUrls;

    /** 点赞数（延迟落库维护） */
    private Integer likeCount;

    /** 点踩数（延迟落库维护） */
    private Integer dislikeCount;

    /** 评论数（延迟落库维护） */
    private Integer commentCount;

    /** 状态：0正常 1已删除 */
    private Integer status;

    /** 是否匿名：0否 1是（显示匿名，后台保留 user_id 用于管理） */
    private Integer isAnonymous;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
