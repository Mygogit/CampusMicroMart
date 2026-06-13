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

import java.util.Collection;
import java.util.function.Supplier;

@Slf4j
@Component
public class DynamicPermissionAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final NacosPermissionLoader permissionLoader;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public DynamicPermissionAuthorizationManager(NacosPermissionLoader permissionLoader) {
        this.permissionLoader = permissionLoader;
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authenticationSupplier,
                                        RequestAuthorizationContext context) {
        HttpServletRequest request = context.getRequest();
        String path = request.getRequestURI();
        String method = request.getMethod();

        log.info("权限管理器检查: {} {}", method, path);

        for (PermissionRule rule : permissionLoader.getPermissionRules()) {
            if (pathMatcher.match(rule.getPath(), path)) {
                if (!"ALL".equals(rule.getMethod()) && !rule.getMethod().equalsIgnoreCase(method)) {
                    continue;
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

        Authentication auth = authenticationSupplier.get();
        if (auth != null && auth.isAuthenticated()) {
            return new AuthorizationDecision(true);
        }
        return new AuthorizationDecision(false);
    }

    private String stripRolePrefix(String authority) {
        if (authority.startsWith("ROLE_")) {
            return authority.substring(5);
        }
        return authority;
    }
}
