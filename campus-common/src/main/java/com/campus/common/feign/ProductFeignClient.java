package com.campus.common.feign;

import com.campus.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "product-service", path = "/product")
public interface ProductFeignClient {

    @PostMapping("/deduct")
    Result<Boolean> deductStock(@RequestParam("productId") Long productId, @RequestParam("quantity") Integer quantity);

    @PostMapping("/rollback")
    Result<Boolean> rollbackStock(@RequestParam("productId") Long productId, @RequestParam("quantity") Integer quantity);

    @PutMapping("/{id}/mark-sold")
    Result<Void> markSold(@PathVariable("id") Long id);

    /**
     * 服务端取价：返回商品快照（价格/库存/卖家/状态）。
     */
    @GetMapping("/{id}/info")
    Result<ProductInfo> getProductInfo(@PathVariable("id") Long id);
}
