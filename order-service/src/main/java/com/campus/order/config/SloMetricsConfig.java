package com.campus.order.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 订单创建 SLO 指标注册。
 * 指标统一打 service="order-service" 标签，供 Grafana「订单服务监控 / SLO 仪表盘」查询。
 * 命名说明：Micrometer 的 Prometheus 规约会为 Counter 自动补 _total 后缀，
 * 因此 order.create.total 暴露为 order_create_total，order.create.success 暴露为 order_create_success_total。
 */
@Configuration
public class SloMetricsConfig {

    @Bean
    public Counter orderCreateTotal(MeterRegistry registry) {
        return Counter.builder("order.create.total")
                .description("订单创建请求总数")
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
                .serviceLevelObjectives(
                        Duration.ofMillis(50),
                        Duration.ofMillis(100),
                        Duration.ofMillis(200),
                        Duration.ofMillis(500),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(5))
                .register(registry);
    }
}
