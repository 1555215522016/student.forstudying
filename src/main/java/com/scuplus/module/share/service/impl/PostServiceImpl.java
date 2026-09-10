package com.scuplus.module.share.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scuplus.common.exception.BusinessException;
import com.scuplus.common.exception.ErrorCode;
import com.scuplus.common.result.PageResult;
import com.scuplus.module.share.dto.PostCreateRequest;
import com.scuplus.module.share.dto.PostVO;
import com.scuplus.module.share.entity.Post;
import com.scuplus.module.share.mapper.PostDocumentMapper;
import com.scuplus.module.share.mapper.PostMapper;
import com.scuplus.module.user.entity.User;
import com.scuplus.module.user.mapper.UserMapper;
import com.scuplus.module.share.service.PostSearchService;
import com.scuplus.module.share.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final PostDocumentMapper postDocumentMapper;
    private final PostSearchService search;
    private final StringRedisTemplate redisTemplate;
    private static final String KEY_LIKES = "post:%d:likes";
    private static final String KEY_DISLIKES = "post:%d:dislikes";

    @Override
    public Long create(Long userid, PostCreateRequest request) {
        Post post = new Post();
        post.setUserId(userid);
        post.setContent(request.getContent());
        post.setStatus(0);
        post.setMediaUrls(request.getMediaUrls());
        post.setIsAnonymous(Boolean.TRUE.equals(request.getIsAnonymous()) ? 1 : 0);
        postMapper.insert(post);
        search.save(postDocumentMapper.toDocument(post));
        return post.getId();
    }

    @Override
    public PageResult<PostVO> list(int page, int size, String sort) {
        if (page < 0 || size < 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, 0);
        switch (sort == null ? "latest" : sort) {
            case "oldest" -> wrapper.orderByAsc(Post::getCreatedAt);          // 最早（按发帖时间正序）
            case "likes" -> wrapper.orderByDesc(Post::getLikeCount);          // 点赞最多（like_count 由任务每30s从Redis同步，允许小延迟）
            default -> wrapper.orderByDesc(Post::getCreatedAt);               // 最新（默认）
        }
        Page<Post> postPage = postMapper.selectPage(
                new Page<>(page, size),
                wrapper
        );
        List<PostVO> list = converToListVo(postPage.getRecords(), false);
// 第一步：获取 List
// 第二步：遍历修改每个元素（因为你要改的是原对象，用 forEach）
        list.forEach(postVO -> {
            postVO.setLikeCount(
                    Optional.ofNullable(redisTemplate.opsForSet().size("post:" + postVO.getId() + ":likes"))
                            .map(Long::intValue)
                            .orElse(0)
            );
            postVO.setDislikeCount(
                    Optional.ofNullable(redisTemplate.opsForSet().size("post:" + postVO.getId() + ":dislikes"))
                            .map(Long::intValue)
                            .orElse(0)
            );
        });
        return PageResult.of(list, postPage.getTotal());
    }

    @Override
    public PostVO detail(Long postid, Long userid) {
        Post post = postMapper.selectById(postid);
        if (post == null || post.getStatus() != 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "帖子已被删除");
        }
        PostVO postVO = convertToVO(post, Boolean.TRUE, null);

        // 只有 userid 不为 null 时才查 Redis（未登录用户不需要查）
        if (userid != null) {
            String userIdStr = userid.toString();
            String likeKey = String.format(KEY_LIKES, postid);
            String dislikeKey = String.format(KEY_DISLIKES, postid);

            Boolean isLiked = redisTemplate.opsForSet().isMember(likeKey, userIdStr);
            Boolean isDisliked = redisTemplate.opsForSet().isMember(dislikeKey, userIdStr);

            if (Boolean.TRUE.equals(isLiked)) {
                postVO.setReactionStatus(1);
            } else if (Boolean.TRUE.equals(isDisliked)) {
                postVO.setReactionStatus(2);
            }
        }
        // 默认 reactionStatus = 0（无操作）

        return postVO;  // ← 必须补上 return！
    }

    public PostVO convertToVO(Post post, Boolean withUser, Map<Long, User> usermap) {
        PostVO vo = new PostVO();
        vo.setId(post.getId());
        vo.setUserId(post.getUserId());
        vo.setContent(post.getContent());
        vo.setLikeCount(post.getLikeCount());
        vo.setDislikeCount(post.getDislikeCount());
        vo.setCreatedAt(post.getCreatedAt());
        vo.setAnonymous(post.getIsAnonymous() == 1);
        vo.setCommentCount(post.getCommentCount());

        if (withUser) {
            vo.setMediaUrls(post.getMediaUrls());
        }

        if (vo.getAnonymous()) {
            vo.setNickname("匿名用户");
            vo.setAvatarUrl(null);
            return vo;
        }
        User user = usermap == null ? userMapper.selectById(post.getUserId()) : usermap.get(post.getUserId());
        if (user != null) {
            vo.setNickname(user.getNickname() != null ? user.getNickname() : user.getStudentId());
            vo.setAvatarUrl(user.getAvatarUrl());
        } else {
            // 用户不存在或已注销，显示占位信息
            vo.setNickname("已注销用户");
            vo.setAvatarUrl(null);
        }
        return vo;
    }

    @Override
    public void delete(Long postId, Long operatorUserId, Integer operatorRole) {
        Post post = postMapper.selectById(postId);
        if (post == null || post.getStatus() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在");
        }
        // 权限：本人 或 管理员 可删
        boolean isOwner = post.getUserId().equals(operatorUserId);
        boolean isAdmin = operatorRole != null && operatorRole == 1;
        if (!isOwner && !isAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权删除该帖子");
        }
        // 软删：只改 status，评论/点赞保留（查询自动过滤）
        post.setStatus(1);
        postMapper.updateById(post);
        // 同步软删到 ES：status 置 1 后，搜索过滤(status=0)就不会再命中它
        // ES 故障不影响删除主流程，save 内部已容错
        search.save(postDocumentMapper.toDocument(post));
    }

    public List<PostVO> converToListVo(List<Post> list, Boolean withUser) {
        List<Long> userIds = list.stream()
                .map(Post::getUserId)
                .filter(Objects::nonNull)
                .distinct()



                .toList();

        // 不能直接抛异常，因为可能全是匿名帖
        Map<Long, User> userMap = userIds.isEmpty() ? new HashMap<>()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u, (old, newVal) -> old));
        return list.stream()
                .map(post -> convertToVO(post, false, userMap))
                .collect(Collectors.toList());


    }
}