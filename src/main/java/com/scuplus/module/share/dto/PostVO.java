package com.scuplus.module.share.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 帖子 VO：返回给前端的帖子信息
 * 列表返回摘要，详情返回全部
 */
@Data
public class PostVO {

    private Long id;

    /** 昵称（isAnonymous=1 时返回"匿名用户"） */
    private String nickname;

    /** 头像（isAnonymous=1 时返回 null） */
    private String avatarUrl;

    /** 内容文字 */
    private String content;

    /** 图片/视频地址数组（详情才返回） */
    private List<String> mediaUrls;

    /** 点赞数 */
    private Integer likeCount;

    /** 点踩数 */
    private Integer dislikeCount;

    /** 评论数 */
    private Integer commentCount;

    /** 发布时间 */
    private LocalDateTime createdAt;

    /** 是否匿名 */
    private Boolean anonymous;

    /**是否点赞or点踩*/
    private Integer reactionStatus;
}
