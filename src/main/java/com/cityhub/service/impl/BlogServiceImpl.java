package com.cityhub.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cityhub.config.CityHubProperties;
import com.cityhub.dto.Result;
import com.cityhub.dto.ScrollResult;
import com.cityhub.entity.Blog;
import com.cityhub.entity.BlogCollect;
import com.cityhub.entity.Follow;
import com.cityhub.entity.Shop;
import com.cityhub.entity.Topic;
import com.cityhub.entity.User;
import com.cityhub.mapper.BlogMapper;
import com.cityhub.service.IBlogCollectService;
import com.cityhub.service.IBlogService;
import com.cityhub.service.IFollowService;
import com.cityhub.service.IShopService;
import com.cityhub.service.ITopicService;
import com.cityhub.service.IUserService;
import com.cityhub.utils.SystemConstants;
import com.cityhub.utils.UserHolder;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.cityhub.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.cityhub.utils.RedisConstants.BLOG_VIEW_KEY;
import static com.cityhub.utils.RedisConstants.FEED_KEY;
import static com.cityhub.utils.RedisConstants.FEED_MAX_SIZE;
import static com.cityhub.utils.RedisConstants.FEED_TTL_DAYS;
import static com.cityhub.utils.RedisConstants.SHOP_GEO_ALL_KEY;
import static com.cityhub.utils.RedisConstants.USER_LIKED_KEY;

/**
 * <p>
 * 探店笔记服务实现 —— CityHub 内容侧核心
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private IFollowService followService;
    @Resource
    private ITopicService topicService;
    @Resource
    private IShopService shopService;
    @Resource
    private IBlogCollectService blogCollectService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CityHubProperties props;

    /* ==================================================================
     *  查询
     * ================================================================== */

    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = query()
                .eq("status", 0)
                .orderByDesc("liked")
                .orderByDesc("id")
                .page(new Page<>(page(current), props.getBlogPageSize(), false));
        fillBlogDetails(page.getRecords());
        return Result.ok(page.getRecords());
    }

    @Override
    public Result queryRecommendFeed(Integer current) {
        // 热度加权：评论 > 点赞 > 收藏 > 浏览
        Page<Blog> page = query()
                .eq("status", 0)
                .last("ORDER BY (liked * 3 + collected * 2 + comments * 4 + view_count / 10) DESC, id DESC")
                .page(new Page<>(page(current), props.getFeedPageSize(), false));
        fillBlogDetails(page.getRecords());
        return Result.ok(page.getRecords());
    }

    @Override
    public Result queryNearbyFeed(Integer current, Double x, Double y) {
        if (x == null || y == null) {
            // 没有定位信息时退化为「最新笔记」
            return queryLatest(current, props.getFeedPageSize());
        }
        // 半径逐级放大：用户在演示数据（杭州）之外时，10 公里内一个商户都没有，
        // 直接返回空列表会让「附近」永远是空白页，体验很差。
        double[] radii = {props.getNearbyRadiusKm(), 50D, 200D};
        List<Long> shopIds = Collections.emptyList();
        for (double radius : radii) {
            shopIds = nearbyShopIds(x, y, radius, 200);
            if (!shopIds.isEmpty()) {
                break;
            }
        }
        if (shopIds.isEmpty()) {
            // 全国范围内都没有带坐标的商户：退化为最新笔记，至少不是白屏
            return queryLatest(current, props.getFeedPageSize());
        }
        Page<Blog> page = query()
                .in("shop_id", shopIds)
                .eq("status", 0)
                .orderByDesc("create_time")
                .orderByDesc("id")
                .page(new Page<>(page(current), props.getFeedPageSize(), false));
        fillBlogDetails(page.getRecords());
        return Result.ok(page.getRecords());
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null || Integer.valueOf(1).equals(blog.getStatus())) {
            return Result.fail("笔记不存在或已被删除");
        }
        increaseViewCount(blog);
        fillBlogDetails(Collections.singletonList(blog));
        return Result.ok(blog);
    }

    @Override
    public Result queryBlogLikes(Long id) {
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        // 保持 ZSet 中的顺序
        Map<Long, User> userMap = userService.listByIds(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        List<Map<String, Object>> vos = new ArrayList<>(ids.size());
        for (Long uid : ids) {
            User u = userMap.get(uid);
            if (u == null) {
                continue;
            }
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("id", u.getId());
            vo.put("nickName", u.getNickName());
            vo.put("icon", u.getIcon());
            vos.add(vo);
        }
        return Result.ok(vos);
    }

    @Override
    public Result queryMyBlog(Integer current) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(pageByUser(userId, current));
    }

    @Override
    public Result queryBlogByUserId(Long userId, Integer current) {
        if (userId == null) {
            return Result.fail("用户不存在");
        }
        return Result.ok(pageByUser(userId, current));
    }

    @Override
    public Result queryBlogByShopId(Long shopId, Integer current) {
        if (shopId == null) {
            return Result.fail("商户不存在");
        }
        Page<Blog> page = query()
                .eq("shop_id", shopId)
                .eq("status", 0)
                .orderByDesc("create_time")
                .orderByDesc("id")
                .page(new Page<>(page(current), props.getBlogPageSize(), false));
        List<Blog> records = page.getRecords();
        fillBlogDetails(records);
        return Result.ok(records);
    }

    @Override
    public Result queryBlogOfTopic(Long topicId, Integer current) {        if (topicId == null) {
            return Result.fail("话题不存在");
        }
        Page<Blog> page = query()
                .eq("topic_id", topicId)
                .eq("status", 0)
                .orderByDesc("create_time")
                .orderByDesc("id")
                .page(new Page<>(page(current), props.getBlogPageSize(), false));
        fillBlogDetails(page.getRecords());
        // 话题浏览量 +1（不严格去重，作为热度参考值）
        topicService.update().setSql("view_count = view_count + 1").eq("id", topicId).update();
        return Result.ok(page.getRecords());
    }

    @Override
    public Result searchBlog(String keyword, Integer current) {
        if (StrUtil.isBlank(keyword)) {
            return queryLatest(current, props.getBlogPageSize());
        }
        String kw = keyword.trim();
        Page<Blog> page = query()
                .eq("status", 0)
                .and(w -> w.like("title", kw).or().like("content", kw).or().like("location", kw))
                .orderByDesc("create_time")
                .orderByDesc("id")
                .page(new Page<>(page(current), props.getBlogPageSize()));
        fillBlogDetails(page.getRecords());
        return Result.ok(page.getRecords(), page.getTotal());
    }

    @Override
    public Result queryMyCollect(Integer current) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        Page<BlogCollect> page = blogCollectService.query()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .page(new Page<>(page(current), props.getBlogPageSize(), false));
        List<Long> ids = page.getRecords().stream().map(BlogCollect::getBlogId).collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        return Result.ok(listByIdsInOrder(ids));
    }

    @Override
    public Result queryMyLiked(Integer current) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        long size = props.getBlogPageSize();
        long start = (long) (page(current) - 1) * size;
        Set<String> ids = stringRedisTemplate.opsForZSet()
                .reverseRange(USER_LIKED_KEY + userId, start, start + size - 1);
        if (ids == null || ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> blogIds = ids.stream().map(Long::valueOf).collect(Collectors.toList());
        return Result.ok(listByIdsInOrder(blogIds));
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        String key = FEED_KEY + userId;
        long now = System.currentTimeMillis();
        long maxScore = (max == null || max <= 0 || max > now) ? now : max;
        long skip = (offset == null || offset < 0) ? 0 : offset;

        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, maxScore, skip, props.getFeedPageSize());

        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(Collections.emptyList());
        scrollResult.setMinTime(0L);
        scrollResult.setOffset(0);
        if (tuples == null || tuples.isEmpty()) {
            return Result.ok(scrollResult);
        }

        List<Long> ids = new ArrayList<>(tuples.size());
        long minTime = 0L;
        int newOffset = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple.getValue() == null) {
                continue;
            }
            ids.add(Long.valueOf(tuple.getValue()));
            long time = tuple.getScore() == null ? 0L : tuple.getScore().longValue();
            // 结果按 score 倒序，同一 score 的最后若干条要靠 offset 跳过
            if (time == minTime) {
                newOffset++;
            } else {
                minTime = time;
                newOffset = 1;
            }
        }
        if (ids.isEmpty()) {
            return Result.ok(scrollResult);
        }
        scrollResult.setList(listByIdsInOrder(ids));
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(newOffset);
        return Result.ok(scrollResult);
    }

    /* ==================================================================
     *  写入
     * ================================================================== */

    @Override
    public Result saveBlog(Blog blog) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        Result invalid = validateBlog(blog);
        if (invalid != null) {
            return invalid;
        }
        blog.setUserId(userId)
                .setId(null)
                .setLiked(0)
                .setComments(0)
                .setCollected(0)
                .setViewCount(0)
                .setStatus(0)
                .setCreateTime(null)
                .setUpdateTime(null);
        if (!save(blog)) {
            return Result.fail("发布笔记失败");
        }
        // 话题计数
        if (blog.getTopicId() != null) {
            topicService.update().setSql("blog_count = blog_count + 1")
                    .eq("id", blog.getTopicId()).update();
        }
        // 推送到粉丝收件箱
        pushToFollowers(userId, blog.getId());
        return Result.ok(blog.getId());
    }

    @Override
    public Result updateBlog(Blog blog) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        if (blog.getId() == null) {
            return Result.fail("笔记不存在");
        }
        Blog exist = getById(blog.getId());
        if (exist == null || Integer.valueOf(1).equals(exist.getStatus())) {
            return Result.fail("笔记不存在或已被删除");
        }
        if (!Objects.equals(exist.getUserId(), userId)) {
            return Result.fail("无权修改他人的笔记");
        }
        Result invalid = validateBlog(blog);
        if (invalid != null) {
            return invalid;
        }
        boolean topicChanged = !Objects.equals(exist.getTopicId(), blog.getTopicId());
        blog.setUserId(userId).setStatus(0);
        updateById(blog);
        if (topicChanged) {
            if (exist.getTopicId() != null) {
                topicService.update().setSql("blog_count = GREATEST(blog_count - 1, 0)")
                        .eq("id", exist.getTopicId()).update();
            }
            if (blog.getTopicId() != null) {
                topicService.update().setSql("blog_count = blog_count + 1")
                        .eq("id", blog.getTopicId()).update();
            }
        }
        return Result.ok(blog.getId());
    }

    @Override
    public Result deleteBlog(Long id) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        Blog blog = getById(id);
        if (blog == null || Integer.valueOf(1).equals(blog.getStatus())) {
            return Result.fail("笔记不存在或已被删除");
        }
        if (!Objects.equals(blog.getUserId(), userId)) {
            return Result.fail("无权删除他人的笔记");
        }
        // 逻辑删除，保留评论与收藏记录
        update().set("status", 1).eq("id", id).update();
        if (blog.getTopicId() != null) {
            topicService.update().setSql("blog_count = GREATEST(blog_count - 1, 0)")
                    .eq("id", blog.getTopicId()).update();
        }
        // 清理粉丝收件箱中的引用
        List<Follow> follows = followService.query().eq("follow_user_id", userId).list();
        for (Follow follow : follows) {
            stringRedisTemplate.opsForZSet().remove(FEED_KEY + follow.getUserId(), id.toString());
        }
        return Result.ok();
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        Blog blog = getById(id);
        if (blog == null || Integer.valueOf(1).equals(blog.getStatus())) {
            return Result.fail("笔记不存在或已被删除");
        }
        String key = BLOG_LIKED_KEY + id;
        String userKey = USER_LIKED_KEY + userId;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        boolean liked;
        if (score == null) {
            update().setSql("liked = liked + 1").eq("id", id).update();
            long now = System.currentTimeMillis();
            stringRedisTemplate.opsForZSet().add(key, userId.toString(), now);
            stringRedisTemplate.opsForZSet().add(userKey, id.toString(), now);
            liked = true;
        } else {
            update().setSql("liked = liked - 1").gt("liked", 0).eq("id", id).update();
            stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            stringRedisTemplate.opsForZSet().remove(userKey, id.toString());
            liked = false;
        }
        Blog fresh = getById(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("liked", fresh == null ? 0 : fresh.getLiked());
        data.put("isLike", liked);
        return Result.ok(data);
    }

    @Override
    public Result collectBlog(Long id) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        Blog blog = getById(id);
        if (blog == null || Integer.valueOf(1).equals(blog.getStatus())) {
            return Result.fail("笔记不存在或已被删除");
        }
        BlogCollect exist = blogCollectService.query()
                .eq("user_id", userId).eq("blog_id", id).one();
        boolean collected;
        if (exist == null) {
            BlogCollect collect = new BlogCollect().setUserId(userId).setBlogId(id);
            if (!blogCollectService.save(collect)) {
                return Result.fail("收藏失败");
            }
            update().setSql("collected = collected + 1").eq("id", id).update();
            collected = true;
        } else {
            blogCollectService.removeById(exist.getId());
            update().setSql("collected = collected - 1").gt("collected", 0).eq("id", id).update();
            collected = false;
        }
        Blog fresh = getById(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("collected", fresh == null ? 0 : fresh.getCollected());
        data.put("isCollected", collected);
        return Result.ok(data);
    }

    /* ==================================================================
     *  内部工具
     * ================================================================== */

    private int page(Integer current) {
        return current == null || current < 1 ? 1 : current;
    }

    private Result validateBlog(Blog blog) {
        if (StrUtil.isBlank(blog.getTitle())) {
            return Result.fail("标题不能为空");
        }
        if (blog.getTitle().trim().length() > 100) {
            return Result.fail("标题最多 100 个字");
        }
        if (StrUtil.isBlank(blog.getContent())) {
            return Result.fail("正文不能为空");
        }
        if (blog.getContent().length() > 2000) {
            return Result.fail("正文最多 2000 个字");
        }
        if (StrUtil.isBlank(blog.getImages())) {
            return Result.fail("至少上传一张图片");
        }
        long imageCount = Arrays.stream(blog.getImages().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).count();
        if (imageCount > SystemConstants.MAX_BLOG_IMAGE_COUNT) {
            return Result.fail("最多上传 " + SystemConstants.MAX_BLOG_IMAGE_COUNT + " 张图片");
        }
        if (blog.getScore() != null && (blog.getScore() < 0 || blog.getScore() > 50)) {
            return Result.fail("评分需要在 0~5 之间");
        }
        if (blog.getShopId() != null && shopService.getById(blog.getShopId()) == null) {
            return Result.fail("关联的商户不存在");
        }
        if (blog.getTopicId() != null && topicService.getById(blog.getTopicId()) == null) {
            return Result.fail("关联的话题不存在");
        }
        return null;
    }

    private Result queryLatest(Integer current, int size) {
        Page<Blog> page = query()
                .eq("status", 0)
                .orderByDesc("create_time")
                .orderByDesc("id")
                .page(new Page<>(page(current), size, false));
        fillBlogDetails(page.getRecords());
        return Result.ok(page.getRecords());
    }

    private List<Blog> pageByUser(Long userId, Integer current) {
        Page<Blog> page = query()
                .eq("user_id", userId)
                .eq("status", 0)
                .orderByDesc("create_time")
                .orderByDesc("id")
                .page(new Page<>(page(current), props.getBlogPageSize(), false));
        List<Blog> records = page.getRecords();
        fillBlogDetails(records);
        return records;
    }

    private List<Blog> listByIdsInOrder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return new ArrayList<>();
        }
        String idStr = distinct.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<Blog> blogs = query().in("id", distinct).eq("status", 0)
                .last("ORDER BY FIELD(id, " + idStr + ")").list();
        fillBlogDetails(blogs);
        return blogs;
    }

    /**
     * 批量补全笔记的作者 / 商户 / 话题信息，以及当前用户的点赞、收藏状态。
     * 用批量查询避免 N+1。
     */
    private void fillBlogDetails(List<Blog> blogs) {
        if (blogs == null || blogs.isEmpty()) {
            return;
        }
        Set<Long> userIds = blogs.stream().map(Blog::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> shopIds = blogs.stream().map(Blog::getShopId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> topicIds = blogs.stream().map(Blog::getTopicId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> blogIds = blogs.stream().map(Blog::getId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, Shop> shopMap = shopIds.isEmpty() ? Collections.emptyMap()
                : shopService.listByIds(shopIds).stream()
                .collect(Collectors.toMap(Shop::getId, s -> s, (a, b) -> a));
        Map<Long, Topic> topicMap = topicIds.isEmpty() ? Collections.emptyMap()
                : topicService.listByIds(topicIds).stream()
                .collect(Collectors.toMap(Topic::getId, t -> t, (a, b) -> a));

        Long currentUserId = UserHolder.getUserId();
        Set<Long> collectedIds = new HashSet<>();
        if (currentUserId != null && !blogIds.isEmpty()) {
            blogCollectService.query()
                    .eq("user_id", currentUserId)
                    .in("blog_id", blogIds)
                    .list()
                    .forEach(c -> collectedIds.add(c.getBlogId()));
        }

        for (Blog blog : blogs) {
            User user = userMap.get(blog.getUserId());
            if (user != null) {
                blog.setName(user.getNickName());
                blog.setIcon(user.getIcon());
            }
            Shop shop = shopMap.get(blog.getShopId());
            if (shop != null) {
                blog.setShopName(shop.getName());
                blog.setShopArea(shop.getArea());
            }
            Topic topic = topicMap.get(blog.getTopicId());
            if (topic != null) {
                blog.setTopicName(topic.getName());
            }
            blog.splitImages();
            blog.setIsLike(currentUserId != null
                    && stringRedisTemplate.opsForZSet()
                    .score(BLOG_LIKED_KEY + blog.getId(), currentUserId.toString()) != null);
            blog.setIsCollected(collectedIds.contains(blog.getId()));
        }
    }

    /**
     * 浏览量 +1，同一访客同一天只计一次。
     */
    private void increaseViewCount(Blog blog) {
        String visitor = currentVisitor();
        if (visitor == null) {
            return;
        }
        String key = BLOG_VIEW_KEY + blog.getId() + ":" + LocalDate.now();
        Long added = stringRedisTemplate.opsForSet().add(key, visitor);
        if (added != null && added > 0) {
            stringRedisTemplate.expire(key, 1, TimeUnit.DAYS);
            update().setSql("view_count = view_count + 1").eq("id", blog.getId()).update();
            blog.setViewCount(blog.getViewCount() == null ? 1 : blog.getViewCount() + 1);
        }
    }

    private String currentVisitor() {
        Long userId = UserHolder.getUserId();
        if (userId != null) {
            return "u:" + userId;
        }
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return "ip:" + attrs.getRequest().getRemoteAddr();
            }
        } catch (Exception ignored) {
            // 非 Web 线程（如测试）直接跳过
        }
        return null;
    }

    /**
     * 把新笔记写入所有粉丝的收件箱，并裁剪、设置过期时间。
     */
    private void pushToFollowers(Long authorId, Long blogId) {
        List<Follow> follows = followService.query().eq("follow_user_id", authorId).list();
        if (follows.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Follow follow : follows) {
            String key = FEED_KEY + follow.getUserId();
            stringRedisTemplate.opsForZSet().add(key, blogId.toString(), now);
            Long size = stringRedisTemplate.opsForZSet().zCard(key);
            if (size != null && size > FEED_MAX_SIZE) {
                stringRedisTemplate.opsForZSet().removeRange(key, 0, size - FEED_MAX_SIZE - 1);
            }
            stringRedisTemplate.expire(key, FEED_TTL_DAYS, TimeUnit.DAYS);
        }
    }

    /**
     * 取定位点附近 {@code radiusKm} 公里内的商户 id，按距离由近到远。
     */
    private List<Long> nearbyShopIds(Double x, Double y, double radiusKm, int limit) {
        try {
            GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                    .radius(SHOP_GEO_ALL_KEY,
                            new Circle(new Point(x, y), new Distance(radiusKm, Metrics.KILOMETERS)),
                            RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                    .includeDistance().sortAscending().limit(limit));
            if (results == null) {
                return Collections.emptyList();
            }
            List<Long> ids = new ArrayList<>();
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> r : results.getContent()) {
                try {
                    ids.add(Long.valueOf(r.getContent().getName()));
                } catch (NumberFormatException ignored) {
                    // 跳过脏数据
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("附近商户查询失败, x=" + x + ", y=" + y + ", cause=" + e);
            return Collections.emptyList();
        }
    }
}
