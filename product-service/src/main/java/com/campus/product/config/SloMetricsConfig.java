package com.campus.product.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SloMetricsConfig {

    @Bean
    public Counter orderCreateTotal(MeterRegistry registry) {
        return Counter.builder("order.create.total")
                .description("订单创建总数")
                .tag("service", "order-service")
                .register(registry);
    }

    @Bean
    public Counter orderCreateSuccess(MeterRegistry registry) {
        return Counter.builder("order.create.success")
                .description("订单创建成功数")
                .tag("service", "order-service")
                .register(registry);
    }

    @Bean
    public Counter orderCreateFailure(MeterRegistry registry) {
        return Counter.builder("order.create.failure")
                .description("订单创建失败数")
                .tag("service", "order-service")
                .register(registry);
    }

    @Bean
    public Timer orderCreateDuration(MeterRegistry registry) {
        return Timer.builder("order.create.duration")
                .description("订单创建耗时")
                .tag("service", "order-service")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }
}
