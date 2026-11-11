package com.campus.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 内部服务间调用的鉴权配置。
 * <p>
 * 服务间 Feign 调用会由 {@link com.campus.common.feign.InternalAuthRequestInterceptor}
 * 自动携带 header（默认 X-Internal-Token），接收方通过
 * {@link DynamicPermissionAuthorizationManager} 识别该令牌并放行受信任的内部调用，
 * 从而避免内部端点被外部用户直接访问。
 * <p>
 * secret 可通过 application.yml 的 {@code internal.auth.secret} 覆盖，建议部署时注入环境变量。
 */
@Data
@Component
@ConfigurationProperties(prefix = "internal.auth")
public class InternalAuthProperties {

    /** 内部鉴权请求头名称 */
    private String header = "X-Internal-Token";

    /** 内部鉴权共享密钥 */
    private String secret = "campus-internal-service-secret-2024";
}
