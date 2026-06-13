package com.campus.common.constant;

import java.util.Map;
import java.util.Set;

public interface OrderStatusConstant {
    Integer WAITING_PAY = 0;
    Integer PAID = 1;
    Integer SHIPPED = 2;
    Integer COMPLETED = 3;
    Integer CANCELLED = 4;

    Map<Integer, Set<Integer>> VALID_TRANSITIONS = Map.of(
        WAITING_PAY, Set.of(PAID, CANCELLED),
        PAID, Set.of(SHIPPED),
        SHIPPED, Set.of(COMPLETED)
    );

    static boolean isValidTransition(Integer from, Integer to) {
        Set<Integer> validTo = VALID_TRANSITIONS.get(from);
        return validTo != null && validTo.contains(to);
    }
}
