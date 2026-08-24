package com.scuplus.module.share.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
@Data
public class CommentVO {
    private Long id;

    /**帖子的id*/
    private Long postId;

    /** 昵称（isAnonymous=1 时返回"匿名用户"） */
    private String nickname;

    /** 头像（isAnonymous=1 时返回 null） */
    private String avatarUrl;

    /** 内容文字 */
    private String content;

    /** 发布时间 */
    private LocalDateTime createdAt;

    /** 是否匿名 */
    private Boolean anonymous;

}
