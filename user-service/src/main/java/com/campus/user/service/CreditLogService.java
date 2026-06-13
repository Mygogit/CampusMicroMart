package com.campus.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.user.entity.CreditLog;

public interface CreditLogService extends IService<CreditLog> {
    void addCreditLog(Long userId, String changeType, int scoreChange, String reason);
}
