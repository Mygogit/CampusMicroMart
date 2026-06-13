package com.campus.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.user.entity.CreditLog;
import com.campus.user.entity.User;
import com.campus.user.mapper.CreditLogMapper;
import com.campus.user.service.CreditLogService;
import com.campus.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class CreditLogServiceImpl extends ServiceImpl<CreditLogMapper, CreditLog> implements CreditLogService {

    @Autowired
    private UserService userService;

    @Override
    @Transactional
    public void addCreditLog(Long userId, String changeType, int scoreChange, String reason) {
        User user = userService.getById(userId);
        if (user == null) {
            return;
        }
        int newScore = user.getCreditScore() + scoreChange;
        user.setCreditScore(newScore);
        userService.updateById(user);

        CreditLog creditLog = new CreditLog();
        creditLog.setUserId(userId);
        creditLog.setChangeType(changeType);
        creditLog.setScoreChange(scoreChange);
        creditLog.setScoreAfter(newScore);
        creditLog.setReason(reason);
        save(creditLog);
        log.info("信用分变更, userId={}, changeType={}, scoreChange={}, scoreAfter={}", userId, changeType, scoreChange, newScore);
    }
}
