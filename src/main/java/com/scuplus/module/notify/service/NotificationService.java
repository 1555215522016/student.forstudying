package com.scuplus.module.notify.service;

import com.scuplus.common.result.PageResult;
import com.scuplus.module.notify.entity.Notification;

import java.util.List;

public interface NotificationService {

    /** 发公告：正文落 t_announcement，再给每个用户插一行通知，在线者实时推 */
    void publishAnnouncement(String title, String content);

    /** 点赞提醒贴主（调用方只在"点赞生效"时触发，自己赞自己不通知） */
    void notifyPostLike(Long postId, Long actorId);

    /** 评论提醒贴主（自己评自己不通知） */
    void notifyPostComment(Long postId, Long actorId, String commentContent);

    /** 按 Last-Event-ID 补发：id > lastEventId 的通知，升序，上限 50 */
    List<Notification> replay(Long userId, Long lastEventId);

    /** 我的通知分页（未读在前） */
    PageResult<Notification> listPage(Long userId, int page, int size);

    /** 未读数（红点） */
    long countUnread(Long userId);

    /** 单条已读（校验归属本人） */
    void markRead(Long userId, Long notificationId);

    /** 全部已读 */
    void markAllRead(Long userId);
}