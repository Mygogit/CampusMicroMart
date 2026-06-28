package com.campus.payment;

import com.alibaba.cloud.nacos.NacosConfigAutoConfiguration;
import com.alibaba.cloud.nacos.discovery.NacosDiscoveryAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.campus", exclude = {
    NacosDiscoveryAutoConfiguration.class,
    NacosConfigAutoConfiguration.class
})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.campus.common.feign")
@EnableScheduling
@MapperScan("com.campus.payment.mapper")
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
