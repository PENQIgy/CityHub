package com.cityhub.utils;

/**
 * CityHub Redis key 统一管理。
 *
 * @author CityHub
 */
public class RedisConstants {
    /* ------------------------------ 登录 ------------------------------ */
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    /* ------------------------------ 商户缓存 ------------------------------ */
    public static final Long CACHE_NULL_TTL = 2L;
    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    /** 附近商户的 GEO 索引，按类型分桶 */
    public static final String SHOP_GEO_KEY = "shop:geo:";
    /** 全部商户的 GEO 索引，用于「附近」Feed 流 */
    public static final String SHOP_GEO_ALL_KEY = "shop:geo:all";

    /* ------------------------------ 秒杀 ------------------------------ */
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String SECKILL_ORDER_KEY = "seckill:order:";

    /* ------------------------------ 探店笔记 ------------------------------ */
    /** 笔记点赞 ZSet：member = userId, score = 点赞时间戳 */
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    /** 用户点赞过的笔记 ZSet：member = blogId, score = 点赞时间戳（反查「我赞过」） */
    public static final String USER_LIKED_KEY = "user:liked:";
    /** 评论点赞 ZSet：member = userId, score = 点赞时间戳 */
    public static final String COMMENT_LIKED_KEY = "comment:liked:";
    /** 笔记浏览去重 Set：blog:view:{blogId}:{yyyyMMdd} */
    public static final String BLOG_VIEW_KEY = "blog:view:";

    /* ------------------------------ Feed 流 ------------------------------ */
    /** 关注流收件箱 ZSet：member = blogId, score = 发布时间戳 */
    public static final String FEED_KEY = "feed:";
    /** 收件箱最大长度，超出后裁剪最旧的数据 */
    public static final long FEED_MAX_SIZE = 1000L;
    /** 收件箱保留时间（天） */
    public static final long FEED_TTL_DAYS = 7L;

    /* ------------------------------ 定位 ------------------------------ */
    /** IP 定位缓存：location:ip:{ip} -> JSON {x,y,city,source} */
    public static final String LOCATION_IP_KEY = "location:ip:";
    /** IP 定位结果缓存时间（天） */
    public static final Long LOCATION_IP_TTL_DAYS = 7L;
    /** IP 定位失败（拿不到结果）的短缓存时间（小时），避免反复请求外部接口 */
    public static final Long LOCATION_IP_MISS_TTL_HOURS = 1L;

    /* ------------------------------ 用户 ------------------------------ */
    public static final String USER_SIGN_KEY = "sign:";
    /** 关注集合 Set：member = 被关注的 userId（用于共同关注） */
    public static final String FOLLOW_KEY = "follow:";
}
