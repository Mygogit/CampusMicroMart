package com.campus.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Component
public class NacosPermissionLoader {

    @Autowired(required = false)
    private com.alibaba.cloud.nacos.NacosConfigManager nacosConfigManager;

    @org.springframework.beans.factory.annotation.Value("${spring.cloud.nacos.config.enabled:true}")
    private boolean nacosConfigEnabled;

    private volatile List<PermissionRule> permissionRules = new ArrayList<>();

    @PostConstruct
    public void init() {
        if (!nacosConfigEnabled || nacosConfigManager == null) {
            log.info("Nacos config disabled or unavailable, using default permission rules");
            loadDefaultRules();
            return;
        }
        loadFromNacos();
        try {
            nacosConfigManager.getConfigService().addListener(
                "campus-permission.yml", "PERMISSION_GROUP", new com.alibaba.nacos.api.config.listener.Listener() {
                    @Override
                    public Executor getExecutor() {
                        return null;
                    }

                    @Override
                    public void receiveConfigInfo(String configInfo) {
                        log.info("Nacos permission config updated");
                        parseConfig(configInfo);
                    }
                });
        } catch (Exception e) {
            log.error("Failed to register Nacos permission listener, using default rules", e);
            loadDefaultRules();
        }
    }

    private void loadFromNacos() {
        try {
            String config = nacosConfigManager.getConfigService()
                    .getConfig("campus-permission.yml", "PERMISSION_GROUP", 3000);
            if (config != null && !config.isBlank()) {
                parseConfig(config);
            } else {
                log.info("No permission config in Nacos, using default rules");
                loadDefaultRules();
            }
        } catch (Exception e) {
            log.error("Failed to load permission config from Nacos, using default rules", e);
            loadDefaultRules();
        }
    }

    @SuppressWarnings("unchecked")
    private void parseConfig(String config) {
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(config);
            if (root == null || !root.containsKey("permissions")) {
                log.warn("Invalid permission config format");
                return;
            }
            List<Map<String, Object>> rawList = (List<Map<String, Object>>) root.get("permissions");
            List<PermissionRule> rules = new ArrayList<>();
            for (Map<String, Object> item : rawList) {
                PermissionRule rule = new PermissionRule();
                rule.setPath((String) item.get("path"));
                rule.setMethod((String) item.getOrDefault("method", "ALL"));
                rule.setPermitAll(Boolean.TRUE.equals(item.get("permitAll")));
                rule.setInternalOnly(Boolean.TRUE.equals(item.get("internalOnly")));
                if (!rule.isPermitAll() && item.containsKey("requiredRoles")) {
                    List<String> roles = (List<String>) item.get("requiredRoles");
                    rule.setRequiredRoles(roles.stream().collect(Collectors.toSet()));
                }
                rules.add(rule);
            }
            permissionRules = rules;
            log.info("Loaded {} permission rules from config", rules.size());
        } catch (Exception e) {
            log.error("Failed to parse permission config", e);
            loadDefaultRules();
        }
    }

    private void loadDefaultRules() {
        List<PermissionRule> rules = new ArrayList<>();

        // ===== 匿名可访问（permitAll）=====
        rules.add(new PermissionRule("/user/login", "POST", null, true));
        rules.add(new PermissionRule("/user/register", "POST", null, true));
        rules.add(new PermissionRule("/product/list", "GET", null, true));
        rules.add(new PermissionRule("/product/search", "GET", null, true));
        rules.add(new PermissionRule("/category/list", "GET", null, true));
        rules.add(new PermissionRule("/uploads/**", "GET", null, true));
        rules.add(new PermissionRule("/payment/callback", "POST", null, true));
        rules.add(new PermissionRule("/doc.html", "ALL", null, true));
        rules.add(new PermissionRule("/webjars/**", "ALL", null, true));
        rules.add(new PermissionRule("/v3/api-docs/**", "ALL", null, true));
        rules.add(new PermissionRule("/swagger-resources/**", "ALL", null, true));
        rules.add(new PermissionRule("/actuator/**", "ALL", null, true));

        // ===== 内部服务调用（internalOnly，仅携带内部令牌的 Feign 可访问）=====
        rules.add(new PermissionRule("/product/deduct", "POST", null, false, true));
        rules.add(new PermissionRule("/product/rollback", "POST", null, false, true));
        rules.add(new PermissionRule("/product/*/mark-sold", "PUT", null, false, true));
        rules.add(new PermissionRule("/order/status", "PUT", null, false, true));
        rules.add(new PermissionRule("/order/exists/**", "ALL", null, false, true));
        rules.add(new PermissionRule("/order/*/cancel-internal", "POST", null, false, true));
        rules.add(new PermissionRule("/user/deposit/freeze", "POST", null, false, true));
        rules.add(new PermissionRule("/user/deposit/unfreeze", "POST", null, false, true));
        rules.add(new PermissionRule("/user/deposit/deduct", "POST", null, false, true));
        rules.add(new PermissionRule("/user/deposit/credit", "POST", null, false, true));

        // ===== 管理员（ADMIN）=====
        rules.add(new PermissionRule("/product/audit/**", "ALL", Set.of("ADMIN"), false));
        rules.add(new PermissionRule("/product/pending", "ALL", Set.of("ADMIN"), false));
        rules.add(new PermissionRule("/product/**", "DELETE", Set.of("ADMIN"), false));
        rules.add(new PermissionRule("/order/*/ship", "POST", Set.of("ADMIN"), false));
        rules.add(new PermissionRule("/order/export", "GET", Set.of("ADMIN"), false));
        rules.add(new PermissionRule("/order/export/**", "ALL", Set.of("ADMIN"), false));
        rules.add(new PermissionRule("/order/batch/**", "ALL", Set.of("ADMIN"), false));
        rules.add(new PermissionRule("/admin/**", "ALL", Set.of("ADMIN"), false));
        rules.add(new PermissionRule("/user/admin/**", "ALL", Set.of("ADMIN"), false));
        rules.add(new PermissionRule("/category", "POST", Set.of("ADMIN"), false));
        rules.add(new PermissionRule("/category", "PUT", Set.of("ADMIN"), false));
        rules.add(new PermissionRule("/category/**", "DELETE", Set.of("ADMIN"), false));

        permissionRules = rules;
        log.info("Loaded {} default permission rules", rules.size());
    }

    public List<PermissionRule> getPermissionRules() {
        return Collections.unmodifiableList(new ArrayList<>(permissionRules));
    }
}
