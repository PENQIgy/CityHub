package com.cityhub.controller;

import com.cityhub.dto.Result;
import com.cityhub.entity.Blog;
import com.cityhub.service.IBlogService;
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
 * 探店笔记前端控制器
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    /* ---------------------------- 写 ---------------------------- */

    /** 发布笔记 */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /** 编辑自己的笔记 */
    @PutMapping
    public Result updateBlog(@RequestBody Blog blog) {
        return blogService.updateBlog(blog);
    }

    /** 删除自己的笔记 */
    @DeleteMapping("/{id}")
    public Result deleteBlog(@PathVariable("id") Long id) {
        return blogService.deleteBlog(id);
    }

    /** 点赞 / 取消点赞 */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    /** 收藏 / 取消收藏 */
    @PutMapping("/collect/{id}")
    public Result collectBlog(@PathVariable("id") Long id) {
        return blogService.collectBlog(id);
    }

    /* ---------------------------- 读 ---------------------------- */

    /** 笔记详情 */
    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        return blogService.queryBlogById(id);
    }

    /** 热门笔记 */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    /** 推荐 Feed 流 */
    @GetMapping("/feed/recommend")
    public Result queryRecommendFeed(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryRecommendFeed(current);
    }

    /** 附近 Feed 流（需要经纬度，缺失时退化为最新） */
    @GetMapping("/feed/nearby")
    public Result queryNearbyFeed(@RequestParam(value = "current", defaultValue = "1") Integer current,
                                  @RequestParam(value = "x", required = false) Double x,
                                  @RequestParam(value = "y", required = false) Double y) {
        return blogService.queryNearbyFeed(current, x, y);
    }

    /** 笔记的点赞用户 */
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        return blogService.queryBlogLikes(id);
    }

    /** 我发布的笔记 */
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryMyBlog(current);
    }

    /** 指定用户的笔记 */
    @GetMapping("/of/user")
    public Result queryBlogByUserId(@RequestParam(value = "current", defaultValue = "1") Integer current,
                                    @RequestParam("id") Long id) {
        return blogService.queryBlogByUserId(id, current);
    }

    /** 某个商户下的探店笔记 */
    @GetMapping("/of/shop")
    public Result queryBlogByShopId(@RequestParam(value = "current", defaultValue = "1") Integer current,
                                    @RequestParam("shopId") Long shopId) {
        return blogService.queryBlogByShopId(shopId, current);
    }

    /** 关注 Feed 流（scroll 分页） */
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(@RequestParam(value = "lastId", required = false) Long max,
                                    @RequestParam(value = "offset", defaultValue = "0") Integer offset) {
        return blogService.queryBlogOfFollow(max, offset);
    }

    /** 话题下的笔记 */
    @GetMapping("/of/topic")
    public Result queryBlogOfTopic(@RequestParam(value = "current", defaultValue = "1") Integer current,
                                   @RequestParam("topicId") Long topicId) {
        return blogService.queryBlogOfTopic(topicId, current);
    }

    /** 我的收藏 */
    @GetMapping("/of/collect")
    public Result queryMyCollect(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryMyCollect(current);
    }

    /** 我点赞过的笔记 */
    @GetMapping("/of/liked")
    public Result queryMyLiked(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryMyLiked(current);
    }

    /** 关键词搜索笔记 */
    @GetMapping("/search")
    public Result searchBlog(@RequestParam(value = "keyword", required = false) String keyword,
                             @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.searchBlog(keyword, current);
    }
}
