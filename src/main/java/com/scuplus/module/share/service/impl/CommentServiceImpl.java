package com.scuplus.module.share.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scuplus.common.exception.BusinessException;
import com.scuplus.common.exception.ErrorCode;
import com.scuplus.common.result.PageResult;
import com.scuplus.module.share.dto.CommentCreateRequest;
import com.scuplus.module.share.dto.CommentVO;
import com.scuplus.module.share.entity.Comment;
import com.scuplus.module.share.entity.Post;
import com.scuplus.module.share.mapper.CommentMapper;
import com.scuplus.module.share.mapper.PostMapper;
import com.scuplus.module.share.service.CommentService;
import com.scuplus.module.user.entity.User;
import com.scuplus.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 评论服务实现
 *
 * 设计点：
 *  - 评论数用 SQL 原子自增（comment_count = comment_count + 1），
 *    评论低频，不需要 Redis 计数（对比点赞用 Redis 高频才需要）
 *  - 匿名：user_id 始终存真实 ID，nickname/avatar 在 VO 层替换为"匿名用户"
 *  - 列表评论批量查用户（selectBatchIds），避免 N+1 查询
 */
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;

    @Override
    public Long create(Long postId, Long userId, CommentCreateRequest req) {
        // 1. 校验帖子存在且正常
        Post post = postMapper.selectById(postId);
        if (post == null || post.getStatus() != 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在");
        }

        // 2. 存评论
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setContent(req.getContent());
        comment.setStatus(0);
        comment.setIsAnonymous(Boolean.TRUE.equals(req.getIsAnonymous()) ? 1 : 0);
        commentMapper.insert(comment);

        // 3. 评论数 +1（SQL 原子自增，天然防并发丢失）
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, postId)
                .setSql("comment_count = comment_count + 1"));

        return comment.getId();
    }

    @Override
    public PageResult<CommentVO> listByPostId(Long postId, int page, int size) {
        // 校验帖子存在且正常
        Post post = postMapper.selectById(postId);
        if (post == null || post.getStatus() != 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在");
        }

        // 分页查评论，按时间正序（楼层式：从早到晚）
        Page<Comment> commentPage = commentMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getPostId, postId)
                        .eq(Comment::getStatus, 0)
                        .orderByAsc(Comment::getCreatedAt)
        );

        List<Comment> comments = commentPage.getRecords();

        // 批量查评论者用户（一次查完，避免 N+1）
        List<Long> userIds = comments.stream()
                .map(Comment::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> userMap = userIds.isEmpty() ? new HashMap<>()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u, (old, newVal) -> old));

        List<CommentVO> voList = comments.stream()
                .map(comment -> convertToVO(comment, userMap))
                .collect(Collectors.toList());

        return PageResult.of(voList, commentPage.getTotal());
    }

    /** Comment → CommentVO，匿名处理评论者显示 */
    private CommentVO convertToVO(Comment comment, Map<Long, User> userMap) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setPostId(comment.getPostId());
        vo.setContent(comment.getContent());
        vo.setCreatedAt(comment.getCreatedAt());

        boolean anonymous = comment.getIsAnonymous() != null && comment.getIsAnonymous() == 1;
        vo.setAnonymous(anonymous);

        if (anonymous) {
            vo.setNickname("匿名用户");
            vo.setAvatarUrl(null);
        } else {
            User user = userMap.get(comment.getUserId());
            if (user != null) {
                vo.setNickname(user.getNickname() != null ? user.getNickname() : user.getStudentId());
                vo.setAvatarUrl(user.getAvatarUrl());
            } else {
                vo.setNickname("已注销用户");
                vo.setAvatarUrl(null);
            }
        }
        return vo;
    }
}