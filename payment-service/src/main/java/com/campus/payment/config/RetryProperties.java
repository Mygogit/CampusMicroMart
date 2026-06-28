package com.campus.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 支付重试与过期配置属性
 * <p>
 * 绑定 application.yml 中 payment.retry、payment.expire、payment.simulate 配置项
 */
@Data
@Component
@ConfigurationProperties(prefix = "payment")
public class RetryProperties {

    /** 重试配置 */
    private Retry retry = new Retry();

    /** 过期配置 */
    private Expire expire = new Expire();

    /** 模拟支付配置 */
    private Simulate simulate = new Simulate();

    @Data
    public static class Retry {
        /** 最大重试次数 */
        private int maxRetries = 3;

        /** 重试间隔列表（毫秒） */
        private List<Long> intervals = List.of(3000L, 10000L, 30000L);
    }

    @Data
    public static class Expire {
        /** 支付过期分钟数 */
        private int minutes = 15;

        /** 过期扫描定时任务 cron 表达式 */
        private String schedulerCron = "0 */5 * * * ?";
    }

    @Data
    public static class Simulate {
        /** 模拟失败率（0.0 ~ 1.0） */
        private double failRate = 0.3;
    }
}
