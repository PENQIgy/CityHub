package com.cityhub.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cityhub.dto.Result;
import com.cityhub.entity.Blog;
import com.cityhub.entity.BlogComments;
import com.cityhub.entity.User;
import com.cityhub.mapper.BlogCommentsMapper;
import com.cityhub.service.IBlogCommentsService;
import com.cityhub.service.IBlogService;
import com.cityhub.service.IUserService;
import com.cityhub.utils.SystemConstants;
import com.cityhub.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.cityhub.utils.RedisConstants.COMMENT_LIKED_KEY;

/**
 * <p>
 * 笔记评论服务实现
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments>
        implements IBlogCommentsService {

    /** 每页一级评论数 */
    private static final int ROOT_PAGE_SIZE = 10;

    @Resource
    private IUserService userService;
    @Resource
    private IBlogService blogService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryCommentsByBlogId(Long blogId, Integer current) {
        if (blogId == null) {
            return Result.fail("笔记不存在");
        }
        int pageNo = current == null || current < 1 ? 1 : current;
        Page<BlogComments> page = query()
                .eq("blog_id", blogId)
                .eq("parent_id", 0)
                .eq("status", 0)
                .orderByDesc("liked")
                .orderByDesc("create_time")
                .page(new Page<>(pageNo, ROOT_PAGE_SIZE, false));
        List<BlogComments> roots = page.getRecords();
        if (roots.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> rootIds = roots.stream().map(BlogComments::getId).collect(Collectors.toList());
        // 取出这些一级评论下的全部回复，用于统计回复数并截取预览
        List<BlogComments> allReplies = query()
                .in("parent_id", rootIds)
                .eq("status", 0)
                .orderByAsc("create_time")
                .list();
        Map<Long, List<BlogComments>> replyMap = allReplies.stream()
                .collect(Collectors.groupingBy(BlogComments::getParentId));

        List<BlogComments> preview = new ArrayList<>();
        for (BlogComments root : roots) {
            List<BlogComments> replies = replyMap.getOrDefault(root.getId(), Collections.emptyList());
            root.setReplyCount(replies.size());
            root.setChildren(replies.stream()
                    .limit(SystemConstants.MAX_REPLY_PREVIEW)
                    .collect(Collectors.toList()));
            preview.addAll(root.getChildren());
        }

        // 批量补全用户信息
        fillUserInfo(roots);
        fillUserInfo(preview);

        Blog blog = blogService.getById(blogId);
        Long authorId = blog == null ? null : blog.getUserId();
        for (BlogComments root : roots) {
            root.setIsAuthor(Objects.equals(root.getUserId(), authorId));
            for (BlogComments child : root.getChildren()) {
                child.setIsAuthor(Objects.equals(child.getUserId(), authorId));
            }
        }
        return Result.ok(roots);
    }

    @Override
    public Result queryReplies(Long commentId, Integer current) {
        if (commentId == null) {
            return Result.fail("评论不存在");
        }
        int pageNo = current == null || current < 1 ? 1 : current;
        Page<BlogComments> page = query()
                .eq("parent_id", commentId)
                .eq("status", 0)
                .orderByAsc("create_time")
                .page(new Page<>(pageNo, 20, false));
        List<BlogComments> replies = page.getRecords();
        fillUserInfo(replies);
        return Result.ok(replies);
    }

    @Override
    public Result countOfBlog(Long blogId) {
        if (blogId == null) {
            return Result.ok(0);
        }
        return Result.ok(query().eq("blog_id", blogId).eq("status", 0).count());
    }

    @Override
    public Result saveComment(BlogComments comment) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        if (comment == null || comment.getBlogId() == null) {
            return Result.fail("笔记不存在");
        }
        if (StrUtil.isBlank(comment.getContent())) {
            return Result.fail("评论内容不能为空");
        }
        String content = comment.getContent().trim();
        if (content.length() > 500) {
            return Result.fail("评论最多 500 个字符");
        }
        Blog blog = blogService.getById(comment.getBlogId());
        if (blog == null || Integer.valueOf(1).equals(blog.getStatus())) {
            return Result.fail("笔记不存在或已被删除");
        }

        long parentId = comment.getParentId() == null ? 0L : comment.getParentId();
        long answerId = comment.getAnswerId() == null ? 0L : comment.getAnswerId();
        if (parentId != 0) {
            BlogComments parent = getById(parentId);
            if (parent == null || Objects.equals(parent.getStatus(), 2)
                    || !Objects.equals(parent.getBlogId(), comment.getBlogId())) {
                return Result.fail("回复的评论不存在");
            }
            // 二级及以下回复统一挂到一级评论下
            if (parent.getParentId() != null && parent.getParentId() != 0) {
                parentId = parent.getParentId();
            }
        }
        if (answerId != 0 && getById(answerId) == null) {
            return Result.fail("回复的评论不存在");
        }

        comment.setId(null)
                .setUserId(userId)
                .setParentId(parentId)
                .setAnswerId(answerId)
                .setLiked(0)
                .setStatus(0)
                .setContent(content)
                .setCreateTime(null)
                .setUpdateTime(null);
        if (!save(comment)) {
            return Result.fail("评论失败");
        }
        blogService.update().setSql("comments = comments + 1")
                .eq("id", comment.getBlogId()).update();
        return Result.ok(comment.getId());
    }

    @Override
    public Result deleteComment(Long id) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        BlogComments comment = getById(id);
        if (comment == null || Objects.equals(comment.getStatus(), 2)) {
            return Result.fail("评论不存在");
        }
        Blog blog = blogService.getById(comment.getBlogId());
        boolean isOwner = Objects.equals(comment.getUserId(), userId);
        boolean isBlogAuthor = blog != null && Objects.equals(blog.getUserId(), userId);
        if (!isOwner && !isBlogAuthor) {
            return Result.fail("无权删除该评论");
        }

        int removed = 1;
        if (comment.getParentId() == null || comment.getParentId() == 0) {
            // 一级评论：连同其下的回复一起隐藏
            int replyCount = query().eq("parent_id", id).eq("status", 0).count();
            update().set("status", 2).eq("parent_id", id).update();
            removed += replyCount;
        }
        update().set("status", 2).eq("id", id).update();
        blogService.update()
                .setSql("comments = GREATEST(comments - " + removed + ", 0)")
                .eq("id", comment.getBlogId()).update();
        return Result.ok();
    }

    @Override
    public Result likeComment(Long id) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        BlogComments comment = getById(id);
        if (comment == null || Objects.equals(comment.getStatus(), 2)) {
            return Result.fail("评论不存在");
        }
        String key = COMMENT_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        boolean liked;
        if (score == null) {
            update().setSql("liked = liked + 1").eq("id", id).update();
            stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            liked = true;
        } else {
            update().setSql("liked = liked - 1").gt("liked", 0).eq("id", id).update();
            stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            liked = false;
        }
        BlogComments fresh = getById(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("liked", fresh == null ? 0 : fresh.getLiked());
        data.put("isLike", liked);
        return Result.ok(data);
    }

    /**
     * 批量补全评论者昵称 / 头像 / 被回复人昵称 / 点赞状态。
     */
    private void fillUserInfo(List<BlogComments> comments) {
        if (comments == null || comments.isEmpty()) {
            return;
        }
        Set<Long> userIds = comments.stream().map(BlogComments::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> answerIds = comments.stream().map(BlogComments::getAnswerId)
                .filter(id -> id != null && id != 0).collect(Collectors.toSet());

        Map<Long, User> userMap = userIds.isEmpty() ? new java.util.HashMap<>()
                : userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, BlogComments> answerMap = answerIds.isEmpty() ? Collections.emptyMap()
                : listByIds(answerIds).stream()
                .collect(Collectors.toMap(BlogComments::getId, c -> c, (a, b) -> a));
        Set<Long> answerUserIds = answerMap.values().stream().map(BlogComments::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!answerUserIds.isEmpty()) {
            userService.listByIds(answerUserIds)
                    .forEach(u -> userMap.putIfAbsent(u.getId(), u));
        }

        Long currentUserId = UserHolder.getUserId();
        for (BlogComments comment : comments) {
            User user = userMap.get(comment.getUserId());
            if (user != null) {
                comment.setName(user.getNickName());
                comment.setIcon(user.getIcon());
            }
            BlogComments answer = answerMap.get(comment.getAnswerId());
            if (answer != null) {
                User answerUser = userMap.get(answer.getUserId());
                if (answerUser != null) {
                    comment.setAnswerName(answerUser.getNickName());
                }
            }
            comment.setIsLike(currentUserId != null
                    && stringRedisTemplate.opsForZSet()
                    .score(COMMENT_LIKED_KEY + comment.getId(), currentUserId.toString()) != null);
        }
    }
}
