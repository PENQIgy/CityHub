package com.cityhub.service;

import com.cityhub.dto.Result;
import com.cityhub.entity.BlogComments;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 笔记评论服务类
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
public interface IBlogCommentsService extends IService<BlogComments> {

    /** 分页查询笔记的评论（一级评论 + 前若干条回复） */
    Result queryCommentsByBlogId(Long blogId, Integer current);

    /** 发表评论 / 回复 */
    Result saveComment(BlogComments comment);

    /** 删除自己的评论 */
    Result deleteComment(Long id);

    /** 评论点赞 / 取消点赞 */
    Result likeComment(Long id);

    /** 某个一级评论下的全部回复 */
    Result queryReplies(Long commentId, Integer current);

    /** 笔记评论总数 */
    Result countOfBlog(Long blogId);
}
