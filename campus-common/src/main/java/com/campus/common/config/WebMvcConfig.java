package com.campus.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 * <p>
 * 认证已统一由 Spring Security（{@code JwtAuthenticationFilter} + {@code DynamicPermissionAuthorizationManager}）
 * 负责。此处不再注册基于 Redis 的 {@code AuthInterceptor}，避免双重鉴权冲突——
 * 否则内部服务间 Feign 调用（已携带内部令牌通过安全过滤器）会被该拦截器误拦截为 401。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
}
