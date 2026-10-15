package com.cityhub.utils;

import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 登录校验拦截器。
 * <p>
 * 按「HTTP 方法 + 路径」精确判定公开接口，避免 {@code excludePathPatterns} 无法区分
 * 读写（例如 {@code GET /blog/{id}} 公开、{@code DELETE /blog/{id}} 必须登录）的问题。
 * 写接口在业务层还有一层登录与归属校验，这里只负责快速返回 401。
 *
 * @author CityHub
 */
public class LoginInterceptor implements HandlerInterceptor {

    /** 公开的 GET 前缀 */
    private static final Set<String> PUBLIC_GET_PREFIXES = Set.of(
            "/shop/",
            "/shop-type/",
            // IP 定位 / 城市列表：未登录也要能拿定位
            "/location/",
            "/voucher/list/",
            "/topic/",
            "/blog/feed/",
            "/blog/likes/",
            "/blog-comments/of/blog/",
            "/blog-comments/count/",
            // 他人主页相关（GET 公开，PUT /user/info 仍需登录）
            "/user/profile/",
            "/user/info/",
            "/user/stats/",
            // 关注关系：列表与「是否关注」允许匿名（匿名时 isFollow 返回 false）
            "/follow/list/",
            "/follow/fans/",
            "/follow/or/not/"
    );

    /** 公开的 GET 精确路径 */
    private static final Set<String> PUBLIC_GET_EXACT = Set.of(
            "/blog/hot",
            "/blog/search",
            "/blog/of/user",
            "/blog/of/shop",
            "/blog/of/topic"
    );

    /** 公开的 POST 精确路径 */
    private static final Set<String> PUBLIC_POST_EXACT = Set.of(
            "/user/code",
            "/user/login"
    );

    /** 笔记详情 GET /blog/{数字id} */
    private static final Pattern BLOG_DETAIL = Pattern.compile("^/blog/\\d+$");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (UserHolder.getUser() != null) {
            return true;
        }
        if (isPublic(request)) {
            return true;
        }
        response.setStatus(401);
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                @Nullable Exception ex) throws Exception {
        // UserHolder 由 RefreshTokenInterceptor 负责清理
    }

    private boolean isPublic(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method)) {
            return PUBLIC_POST_EXACT.contains(uri);
        }
        if (!"GET".equalsIgnoreCase(method)) {
            return false;
        }
        if (PUBLIC_GET_EXACT.contains(uri) || BLOG_DETAIL.matcher(uri).matches()) {
            return true;
        }
        for (String prefix : PUBLIC_GET_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
