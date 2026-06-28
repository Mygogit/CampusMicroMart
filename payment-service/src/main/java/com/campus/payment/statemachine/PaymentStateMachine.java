package com.campus.payment.statemachine;

import com.campus.common.constant.PaymentStatusConstant;
import com.campus.common.exception.BusinessException;
import com.campus.payment.constant.PaymentErrorCode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 支付状态机
 * <p>
 * 管理支付状态之间的合法转移规则。
 * 状态流转路径：
 * <pre>
 *   WAITING_PAY(0) → PROCESSING(1) / EXPIRED(4)
 *   PROCESSING(1)  → SUCCESS(2) / FAIL(3)
 *   FAIL(3)        → RETRYING(5)
 *   RETRYING(5)    → SUCCESS(2) / FAIL(3)
 *   SUCCESS(2)、EXPIRED(4) 为终态，不可再转移
 * </pre>
 */
@Component
public class PaymentStateMachine {

    /**
     * 状态转移矩阵：from → 允许的 to 集合
     */
    private static final Map<Integer, Set<Integer>> TRANSITIONS = new HashMap<>();

    static {
        TRANSITIONS.put(PaymentStatusConstant.WAITING_PAY.getCode(),
                Set.of(PaymentStatusConstant.PROCESSING.getCode(), PaymentStatusConstant.EXPIRED.getCode()));
        TRANSITIONS.put(PaymentStatusConstant.PROCESSING.getCode(),
                Set.of(PaymentStatusConstant.SUCCESS.getCode(), PaymentStatusConstant.FAIL.getCode()));
        TRANSITIONS.put(PaymentStatusConstant.FAIL.getCode(),
                Set.of(PaymentStatusConstant.RETRYING.getCode()));
        TRANSITIONS.put(PaymentStatusConstant.RETRYING.getCode(),
                Set.of(PaymentStatusConstant.SUCCESS.getCode(), PaymentStatusConstant.FAIL.getCode()));
        // SUCCESS(2) 和 EXPIRED(4) 是终态，无出边
    }

    /**
     * 判断 from → to 是否是一个合法的状态转移
     *
     * @param fromStatus 当前状态码
     * @param toStatus   目标状态码
     * @return true 表示合法转移
     */
    public boolean canTransition(Integer fromStatus, Integer toStatus) {
        if (fromStatus == null) {
            // 新建支付：允许进入初始状态 WAITING_PAY
            return PaymentStatusConstant.WAITING_PAY.getCode().equals(toStatus);
        }
        Set<Integer> allowed = TRANSITIONS.get(fromStatus);
        return allowed != null && allowed.contains(toStatus);
    }

    /**
     * 校验状态转移合法性，不合法时抛出 BusinessException
     *
     * @param fromStatus 当前状态码
     * @param toStatus   目标状态码
     * @throws BusinessException 状态转移非法
     */
    public void validateTransition(Integer fromStatus, Integer toStatus) {
        if (!canTransition(fromStatus, toStatus)) {
            String fromDesc = PaymentStatusConstant.getByCode(fromStatus) != null
                    ? PaymentStatusConstant.getByCode(fromStatus).getDescription()
                    : "未知(" + fromStatus + ")";
            String toDesc = PaymentStatusConstant.getByCode(toStatus) != null
                    ? PaymentStatusConstant.getByCode(toStatus).getDescription()
                    : "未知(" + toStatus + ")";
            throw new BusinessException(
                    PaymentErrorCode.STATE_TRANSITION_INVALID.getCode(),
                    PaymentErrorCode.STATE_TRANSITION_INVALID.getMessage()
                            + "：[" + fromDesc + " → " + toDesc + "]"
            );
        }
    }
}
