package com.cityhub.utils;

/**
 * 全局常量。
 *
 * @author CityHub
 */
public class SystemConstants {
    public static final String USER_NICK_NAME_PREFIX = "user_";
    /** 默认分页大小 */
    public static final int DEFAULT_PAGE_SIZE = 5;
    /** 列表最大分页大小 */
    public static final int MAX_PAGE_SIZE = 10;
    /** 探店笔记最多图片数 */
    public static final int MAX_BLOG_IMAGE_COUNT = 9;
    /** 一级评论下最多预览的回复数 */
    public static final int MAX_REPLY_PREVIEW = 2;
    /**
     * 普通代金券的「不限量」库存标记，落库为 0。
     * <p>
     * 用 0 而不是 -1：取消订单时会执行 {@code stock = stock + 1}，-1 会被加回 0，
     * 而 0 恰好是「限量且已售罄」的取值，会让不限量券被误判成售罄。
     * 判定不限量的规则统一为 {@code stock <= 0}（见 isLimitedStock）。
     */
    public static final int VOUCHER_UNLIMITED_STOCK = 0;
}
