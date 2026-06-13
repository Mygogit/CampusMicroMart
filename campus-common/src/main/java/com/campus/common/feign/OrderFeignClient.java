package com.campus.common.feign;

import com.campus.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "order-service", path = "/order")
public interface OrderFeignClient {

    @PutMapping("/status")
    Result<Boolean> updateOrderStatus(@RequestParam("orderId") Long orderId, @RequestParam("status") Integer status);

    @GetMapping("/exists/{id}")
    Result<Boolean> exists(@PathVariable("id") Long id);
}
