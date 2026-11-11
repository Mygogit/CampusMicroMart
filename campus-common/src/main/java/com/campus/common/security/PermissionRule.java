package com.campus.common.security;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * 权限规则：描述某一路径 + 方法的访问控制要求。
 * <ul>
 *     <li>{@code permitAll}：匿名可访问；</li>
 *     <li>{@code internalOnly}：仅允许携带内部服务令牌的调用（服务间 Feign）；</li>
 *     <li>{@code requiredRoles}：仅允许指定角色的已登录用户；</li>
 *     <li>三者皆不满足时，由权限管理器按「已登录即放行 / 未登录拒绝」兜底。</li>
 * </ul>
 */
@Data
@NoArgsConstructor
public class PermissionRule {
    private String path;
    private String method = "ALL";
    private Set<String> requiredRoles;
    private boolean permitAll;
    private boolean internalOnly;

    public PermissionRule(String path, String method, Set<String> requiredRoles, boolean permitAll) {
        this.path = path;
        this.method = method;
        this.requiredRoles = requiredRoles;
        this.permitAll = permitAll;
        this.internalOnly = false;
    }

    public PermissionRule(String path, String method, Set<String> requiredRoles, boolean permitAll, boolean internalOnly) {
        this.path = path;
        this.method = method;
        this.requiredRoles = requiredRoles;
        this.permitAll = permitAll;
        this.internalOnly = internalOnly;
    }
}
