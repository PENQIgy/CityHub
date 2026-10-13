package com.cityhub.config;

import com.cityhub.utils.LoginInterceptor;
import com.cityhub.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 先刷新 token 有效期并写入 UserHolder
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(0);
        // 再校验登录态；公开接口的判定放在 LoginInterceptor 内部按方法+路径处理
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns("/**")
                .order(1);
    }
}
