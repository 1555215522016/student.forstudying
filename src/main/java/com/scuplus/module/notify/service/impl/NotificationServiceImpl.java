package com.scuplus.module.notify.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scuplus.common.exception.BusinessException;
import com.scuplus.common.exception.ErrorCode;
import com.scuplus.common.result.PageResult;
import com.scuplus.module.notify.entity.Announcement;
import com.scuplus.module.notify.entity.Notification;
import com.scuplus.module.notify.mapper.AnnouncementMapper;
import com.scuplus.module.notify.mapper.NotificationMapper;
import com.scuplus.module.notify.service.NotificationService;
import com.scuplus.module.notify.sse.SseEmitterRegistry;
import com.scuplus.module.share.entity.Post;
import com.scuplus.module.share.mapper.PostMapper;
import com.scuplus.module.user.entity.User;
import com.scuplus.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知服务：MySQL 落库兜底 + SSE 实时推 + Last-Event-ID 断线补发
 *
 * 最终一致性：先写 MySQL（权威），再推 SSE（尽力实时）；
 * 离线/断线用户下次连上时按 Last-Event-ID 从库里捞 id 之后的通知补发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int REPLAY_LIMIT = 50;
    private static final int COMMENT_SNIPPET_LEN = 50;

    private final NotificationMapper notificationMapper;
    private final AnnouncementMapper announcementMapper;
    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final SseEmitterRegistry registry;
    private final ObjectMapper objectMapper;

    @Override
    public void publishAnnouncement(String title, String content) {
        Announcement announcement = new Announcement();
        announcement.setTitle(title);
        announcement.setContent(content);
        announcementMapper.insert(announcement);

        // 给每个用户插一行通知（is_read 要按人记 → 必须每人一行），只查 id 列避免捞无关字段
        String snippet = "【公告】" + title;
        for (User user : userMapper.selectList(
                new LambdaQueryWrapper<User>().select(User::getId))) {
            Notification n = new Notification();
            n.setUserId(user.getId());
            n.setType(Notification.TYPE_ANNOUNCEMENT);
            n.setSourceId(announcement.getId());
            n.setContent(snippet);
            notificationMapper.insert(n);
            push(user.getId(), n);
        }
    }

    @Override
    public void notifyPostLike(Long postId, Long actorId) {
        Post post = postMapper.selectById(postId);
        if (post == null || actorId.equals(post.getUserId())) {
            return; // 帖子没了 或 自己赞自己，不打扰
        }
        Notification n = new Notification();
        n.setUserId(post.getUserId());
        n.setType(Notification.TYPE_LIKE);
        n.setSourceId(postId);
        n.setContent(actorName(actorId) + " 赞了你的帖子");
        notificationMapper.insert(n);
        push(post.getUserId(), n);
    }

    @Override
    public void notifyPostComment(Long postId, Long actorId, String commentContent) {
        Post post = postMapper.selectById(postId);
        if (post == null || actorId.equals(post.getUserId())) {
            return;
        }
        String snippet = commentContent == null ? "" : commentContent;
        if (snippet.length() > COMMENT_SNIPPET_LEN) {
            snippet = snippet.substring(0, COMMENT_SNIPPET_LEN) + "...";
        }
        Notification n = new Notification();
        n.setUserId(post.getUserId());
        n.setType(Notification.TYPE_COMMENT);
        n.setSourceId(postId);
        n.setContent(actorName(actorId) + " 评论了你的帖子：" + snippet);
        notificationMapper.insert(n);
        push(post.getUserId(), n);
    }

    @Override
    public List<Notification> replay(Long userId, Long lastEventId) {
        List<Notification> list = notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .gt(Notification::getId, lastEventId)
                        .orderByAsc(Notification::getId)
                        .last("LIMIT " + REPLAY_LIMIT));
        return list == null ? List.of() : list;
    }

    @Override
    public PageResult<Notification> listPage(Long userId, int page, int size) {
        Page<Notification> result = notificationMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByAsc(Notification::getIsRead)   // 未读在前
                        .orderByDesc(Notification::getId));    // 最新在前
        return PageResult.of(result.getRecords(), result.getTotal());
    }

    @Override
    public long countUnread(Long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
    }

    @Override
    public void markRead(Long userId, Long notificationId) {
        Notification n = notificationMapper.selectById(notificationId);
        if (n == null || !n.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "通知不存在");
        }
        if (n.getIsRead() == null || n.getIsRead() == 0) {
            n.setIsRead(1);
            notificationMapper.updateById(n);
        }
    }

    @Override
    public void markAllRead(Long userId) {
        Notification patch = new Notification();
        patch.setIsRead(1);
        notificationMapper.update(patch, new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
    }

    /** 评论者昵称兜底：用户没了给个占位 */
    private String actorName(Long actorId) {
        User actor = userMapper.selectById(actorId);
        if (actor == null) {
            return "有人";
        }
        return actor.getNickname() != null ? actor.getNickname() : actor.getStudentId();
    }

    /** 落库后给在线用户实时推；离线用户靠 Last-Event-ID 下次补发（MySQL 已兜底，不丢） */
    private void push(Long userId, Notification n) {
        if (!registry.isOnline(userId)) {
            return;
        }
        // DB 默认值不会回填到刚 insert 的内存对象，SSE payload 补上，前端拿到完整字段
        if (n.getIsRead() == null) {
            n.setIsRead(0);
        }
        if (n.getCreatedAt() == null) {
            n.setCreatedAt(LocalDateTime.now());
        }
        try {
            String json = objectMapper.writeValueAsString(n);
            registry.sendToUser(userId, SseEmitter.event()
                    .id(String.valueOf(n.getId()))
                    .name("notification")
                    .data(json));
        } catch (JsonProcessingException e) {
            log.error("通知序列化失败，无法实时推送: userId={} notificationId={}", userId, n.getId(), e);
        }
    }
}