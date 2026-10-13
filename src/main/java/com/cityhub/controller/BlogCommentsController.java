package com.cityhub.controller;

import com.cityhub.dto.Result;
import com.cityhub.entity.BlogComments;
import com.cityhub.service.IBlogCommentsService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 * 笔记评论前端控制器
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {

    @Resource
    private IBlogCommentsService blogCommentsService;

    /** 分页查询笔记的评论（一级评论 + 回复预览） */
    @GetMapping("/of/blog/{blogId}")
    public Result queryCommentsByBlogId(@PathVariable("blogId") Long blogId,
                                        @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogCommentsService.queryCommentsByBlogId(blogId, current);
    }

    /** 某个一级评论下的全部回复 */
    @GetMapping("/replies/{commentId}")
    public Result queryReplies(@PathVariable("commentId") Long commentId,
                               @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogCommentsService.queryReplies(commentId, current);
    }

    /** 笔记评论总数 */
    @GetMapping("/count/{blogId}")
    public Result countOfBlog(@PathVariable("blogId") Long blogId) {
        return blogCommentsService.countOfBlog(blogId);
    }

    /** 发表评论 / 回复 */
    @PostMapping
    public Result saveComment(@RequestBody BlogComments comment) {
        return blogCommentsService.saveComment(comment);
    }

    /** 删除评论（评论作者或笔记作者） */
    @DeleteMapping("/{id}")
    public Result deleteComment(@PathVariable("id") Long id) {
        return blogCommentsService.deleteComment(id);
    }

    /** 评论点赞 / 取消点赞 */
    @PutMapping("/like/{id}")
    public Result likeComment(@PathVariable("id") Long id) {
        return blogCommentsService.likeComment(id);
    }
}
