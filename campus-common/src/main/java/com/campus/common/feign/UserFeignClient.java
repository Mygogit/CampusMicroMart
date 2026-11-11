package com.campus.common.feign;

import com.campus.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient(name = "user-service", path = "/user")
public interface UserFeignClient {

    @PostMapping("/deposit/freeze")
    Result<Boolean> freezeDeposit(@RequestParam("userId") Long userId, @RequestParam("amount") BigDecimal amount);

    @PostMapping("/deposit/unfreeze")
    Result<Boolean> unfreezeDeposit(@RequestParam("userId") Long userId, @RequestParam("amount") BigDecimal amount);

    @PostMapping("/deposit/deduct")
    Result<Boolean> deductDeposit(@RequestParam("userId") Long userId, @RequestParam("amount") BigDecimal amount);

    /**
     * 结算：给卖家入账（确认收货后由 order-service 调用）。
     */
    @PostMapping("/deposit/credit")
    Result<Boolean> creditDeposit(@RequestParam("userId") Long userId, @RequestParam("amount") BigDecimal amount);
}
