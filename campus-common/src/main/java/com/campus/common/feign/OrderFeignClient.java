package com.campus.common.feign;

import com.campus.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient(name = "order-service", path = "/order")
public interface OrderFeignClient {

    @PutMapping("/status")
    Result<Boolean> updateOrderStatus(@RequestParam("orderId") Long orderId, @RequestParam("status") Integer status);

    @GetMapping("/exists/{id}")
    Result<Boolean> exists(@PathVariable("id") Long id);

    /**
     * 服务端校验：返回订单应付金额，用于支付金额一致性校验。
     */
    @GetMapping("/{id}/amount")
    Result<BigDecimal> getAmount(@PathVariable("id") Long id);

    /**
     * 内部取消订单（支付超时/重试耗尽时回滚库存并取消）。
     */
    @PostMapping("/{id}/cancel-internal")
    Result<Boolean> cancelOrder(@PathVariable("id") Long id);
}
