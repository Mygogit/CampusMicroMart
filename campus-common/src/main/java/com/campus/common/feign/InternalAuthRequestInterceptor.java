package com.campus.common.feign;

import com.campus.common.security.InternalAuthProperties;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

/**
 * 为所有 Feign 服务间调用统一注入内部鉴权头。
 * <p>
 * 该拦截器由 Spring Cloud OpenFeign 全局应用于所有 @FeignClient，
 * 使被调用方（user/product/order/payment）能识别并放行内部调用，
 * 解决此前「内部 Feign 调用被 Spring Security 403 阻断」的问题。
 */
@Component
public class InternalAuthRequestInterceptor implements RequestInterceptor {

    private final InternalAuthProperties properties;

    public InternalAuthRequestInterceptor(InternalAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public void apply(RequestTemplate template) {
        template.header(properties.getHeader(), properties.getSecret());
    }
}
