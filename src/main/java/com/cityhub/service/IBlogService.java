package com.cityhub.service;

import com.cityhub.dto.Result;
import com.cityhub.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 探店笔记服务类
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /** 热门笔记（按点赞量） */
    Result queryHotBlog(Integer current);

    /** 推荐 Feed 流（按热度加权） */
    Result queryRecommendFeed(Integer current);

    /** 附近 Feed 流（依赖商户 GEO 索引） */
    Result queryNearbyFeed(Integer current, Double x, Double y);

    /** 笔记详情（会计一次浏览） */
    Result queryBlogById(Long id);

    /** 发布笔记，并推送到粉丝收件箱 */
    Result saveBlog(Blog blog);

    /** 编辑自己的笔记 */
    Result updateBlog(Blog blog);

    /** 删除（隐藏）自己的笔记 */
    Result deleteBlog(Long id);

    /** 点赞 / 取消点赞 */
    Result likeBlog(Long id);

    /** 收藏 / 取消收藏 */
    Result collectBlog(Long id);

    /** 笔记的点赞用户（最多 5 个） */
    Result queryBlogLikes(Long id);

    /** 我发布的笔记 */
    Result queryMyBlog(Integer current);

    /** 指定用户发布的笔记 */
    Result queryBlogByUserId(Long userId, Integer current);

    /** 某个商户下的探店笔记 */
    Result queryBlogByShopId(Long shopId, Integer current);

    /** 关注 Feed 流（scroll 分页） */
    Result queryBlogOfFollow(Long max, Integer offset);

    /** 话题下的笔记 */
    Result queryBlogOfTopic(Long topicId, Integer current);

    /** 关键词搜索笔记 */
    Result searchBlog(String keyword, Integer current);

    /** 我的收藏 */
    Result queryMyCollect(Integer current);

    /** 我点赞过的笔记 */
    Result queryMyLiked(Integer current);
}
