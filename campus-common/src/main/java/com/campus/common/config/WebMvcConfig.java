package com.campus.common.config;

import com.campus.common.interceptor.AuthInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnMissingBean(name = "securityFilterChain")
public class WebMvcConfig implements WebMvcConfigurer {

    private final StringRedisTemplate stringRedisTemplate;

    public WebMvcConfig(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/user/login",
                    "/user/register",
                    "/product/list",
                    "/product/search",
                    "/product/deduct",
                    "/product/rollback",
                    "/product/mark-sold",
                    "/category/list",
                    "/uploads/**",
                    "/order/status",
                    "/order/exists/**",
                    "/doc.html",
                    "/webjars/**",
                    "/v3/api-docs/**",
                    "/swagger-resources/**"
                );
    }
}
