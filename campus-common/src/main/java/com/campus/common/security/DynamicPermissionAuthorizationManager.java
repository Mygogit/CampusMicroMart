package com.campus.common.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.function.Supplier;

@Slf4j
@Component
public class DynamicPermissionAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final NacosPermissionLoader permissionLoader;
    private final InternalAuthProperties internalAuthProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public DynamicPermissionAuthorizationManager(NacosPermissionLoader permissionLoader,
                                                 InternalAuthProperties internalAuthProperties) {
        this.permissionLoader = permissionLoader;
        this.internalAuthProperties = internalAuthProperties;
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authenticationSupplier,
                                        RequestAuthorizationContext context) {
        HttpServletRequest request = context.getRequest();
        String path = request.getRequestURI();
        String method = request.getMethod();

        // 内部服务调用：携带有效内部令牌，直接放行（服务间 Feign 调用受信任）
        if (isInternalCall(request)) {
            return new AuthorizationDecision(true);
        }

        for (PermissionRule rule : permissionLoader.getPermissionRules()) {
            if (pathMatcher.match(rule.getPath(), path)) {
                if (!"ALL".equals(rule.getMethod()) && !rule.getMethod().equalsIgnoreCase(method)) {
                    continue;
                }
                if (rule.isInternalOnly()) {
                    // 未携带内部令牌（已在上方被放行）→ 外部请求一律拒绝
                    return new AuthorizationDecision(false);
                }
                if (rule.isPermitAll()) {
                    return new AuthorizationDecision(true);
                }
                Authentication auth = authenticationSupplier.get();
                if (auth == null || !auth.isAuthenticated()) {
                    return new AuthorizationDecision(false);
                }
                Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
                for (GrantedAuthority authority : authorities) {
                    if (rule.getRequiredRoles() != null
                            && rule.getRequiredRoles().contains(stripRolePrefix(authority.getAuthority()))) {
                        return new AuthorizationDecision(true);
                    }
                }
                return new AuthorizationDecision(false);
            }
        }

        // 兜底：未匹配任何规则时，仅允许已登录用户（角色/属主校验由各服务在业务层完成）
        Authentication auth = authenticationSupplier.get();
        if (auth != null && auth.isAuthenticated()) {
            return new AuthorizationDecision(true);
        }
        return new AuthorizationDecision(false);
    }

    /**
     * 校验请求是否携带有效的内部服务令牌。
     */
    private boolean isInternalCall(HttpServletRequest request) {
        String header = internalAuthProperties.getHeader();
        String secret = internalAuthProperties.getSecret();
        if (header == null || secret == null) {
            return false;
        }
        String value = request.getHeader(header);
        if (value == null) {
            return false;
        }
        // 恒定时间比较，避免时序侧信道
        return MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8),
                value.getBytes(StandardCharsets.UTF_8));
    }

    private String stripRolePrefix(String authority) {
        if (authority.startsWith("ROLE_")) {
            return authority.substring(5);
        }
        return authority;
    }
}
