package com.campus.common.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionRule {
    private String path;
    private String method = "ALL";
    private Set<String> requiredRoles;
    private boolean permitAll;
}
