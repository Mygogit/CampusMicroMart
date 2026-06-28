package com.campus.payment.statemachine;

import com.campus.common.constant.PaymentStatusConstant;
import com.campus.common.exception.BusinessException;
import com.campus.payment.constant.PaymentErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 支付状态机单元测试
 * <p>
 * 验证所有合法与非法状态转移规则，覆盖率目标 100%
 */
@DisplayName("支付状态机")
class PaymentStateMachineTest {

    private PaymentStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new PaymentStateMachine();
    }

    // ==================== WAITING_PAY(0) 转移 ====================

    @Nested
    @DisplayName("从 WAITING_PAY(0) 出发")
    class FromWaitingPay {

        @Test
        @DisplayName("✓ WAITING_PAY(0) → PROCESSING(1) 允许")
        void shouldAllowTransitionFromWaitingPayToProcessing() {
            assertTrue(stateMachine.canTransition(
                    PaymentStatusConstant.WAITING_PAY.getCode(),
                    PaymentStatusConstant.PROCESSING.getCode()));
        }

        @Test
        @DisplayName("✓ WAITING_PAY(0) → EXPIRED(4) 允许")
        void shouldAllowTransitionFromWaitingPayToExpired() {
            assertTrue(stateMachine.canTransition(
                    PaymentStatusConstant.WAITING_PAY.getCode(),
                    PaymentStatusConstant.EXPIRED.getCode()));
        }

        @Test
        @DisplayName("✗ WAITING_PAY(0) → SUCCESS(2) 禁止")
        void shouldRejectTransitionFromWaitingPayToSuccess() {
            assertFalse(stateMachine.canTransition(
                    PaymentStatusConstant.WAITING_PAY.getCode(),
                    PaymentStatusConstant.SUCCESS.getCode()));
        }

        @Test
        @DisplayName("✗ WAITING_PAY(0) → FAIL(3) 禁止")
        void shouldRejectTransitionFromWaitingPayToFail() {
            assertFalse(stateMachine.canTransition(
                    PaymentStatusConstant.WAITING_PAY.getCode(),
                    PaymentStatusConstant.FAIL.getCode()));
        }

        @Test
        @DisplayName("✗ WAITING_PAY(0) → RETRYING(5) 禁止")
        void shouldRejectTransitionFromWaitingPayToRetrying() {
            assertFalse(stateMachine.canTransition(
                    PaymentStatusConstant.WAITING_PAY.getCode(),
                    PaymentStatusConstant.RETRYING.getCode()));
        }
    }

    // ==================== PROCESSING(1) 转移 ====================

    @Nested
    @DisplayName("从 PROCESSING(1) 出发")
    class FromProcessing {

        @Test
        @DisplayName("✓ PROCESSING(1) → SUCCESS(2) 允许")
        void shouldAllowTransitionFromProcessingToSuccess() {
            assertTrue(stateMachine.canTransition(
                    PaymentStatusConstant.PROCESSING.getCode(),
                    PaymentStatusConstant.SUCCESS.getCode()));
        }

        @Test
        @DisplayName("✓ PROCESSING(1) → FAIL(3) 允许")
        void shouldAllowTransitionFromProcessingToFail() {
            assertTrue(stateMachine.canTransition(
                    PaymentStatusConstant.PROCESSING.getCode(),
                    PaymentStatusConstant.FAIL.getCode()));
        }

        @Test
        @DisplayName("✗ PROCESSING(1) → EXPIRED(4) 禁止")
        void shouldRejectTransitionFromProcessingToExpired() {
            assertFalse(stateMachine.canTransition(
                    PaymentStatusConstant.PROCESSING.getCode(),
                    PaymentStatusConstant.EXPIRED.getCode()));
        }

        @Test
        @DisplayName("✗ PROCESSING(1) → WAITING_PAY(0) 禁止")
        void shouldRejectTransitionFromProcessingToWaitingPay() {
            assertFalse(stateMachine.canTransition(
                    PaymentStatusConstant.PROCESSING.getCode(),
                    PaymentStatusConstant.WAITING_PAY.getCode()));
        }

        @Test
        @DisplayName("✗ PROCESSING(1) → RETRYING(5) 禁止")
        void shouldRejectTransitionFromProcessingToRetrying() {
            assertFalse(stateMachine.canTransition(
                    PaymentStatusConstant.PROCESSING.getCode(),
                    PaymentStatusConstant.RETRYING.getCode()));
        }
    }

    // ==================== FAIL(3) 转移 ====================

    @Nested
    @DisplayName("从 FAIL(3) 出发")
    class FromFail {

        @Test
        @DisplayName("✓ FAIL(3) → RETRYING(5) 允许")
        void shouldAllowTransitionFromFailToRetrying() {
            assertTrue(stateMachine.canTransition(
                    PaymentStatusConstant.FAIL.getCode(),
                    PaymentStatusConstant.RETRYING.getCode()));
        }

        @Test
        @DisplayName("✗ FAIL(3) → SUCCESS(2) 禁止（必须经过 RETRYING）")
        void shouldRejectTransitionFromFailToSuccess() {
            assertFalse(stateMachine.canTransition(
                    PaymentStatusConstant.FAIL.getCode(),
                    PaymentStatusConstant.SUCCESS.getCode()));
        }

        @Test
        @DisplayName("✗ FAIL(3) → FAIL(3) 禁止（不允许自循环）")
        void shouldRejectTransitionFromFailToFail() {
            assertFalse(stateMachine.canTransition(
                    PaymentStatusConstant.FAIL.getCode(),
                    PaymentStatusConstant.FAIL.getCode()));
        }

        @Test
        @DisplayName("✗ FAIL(3) → WAITING_PAY(0) 禁止")
        void shouldRejectTransitionFromFailToWaitingPay() {
            assertFalse(stateMachine.canTransition(
                    PaymentStatusConstant.FAIL.getCode(),
                    PaymentStatusConstant.WAITING_PAY.getCode()));
        }
    }

    // ==================== RETRYING(5) 转移 ====================

    @Nested
    @DisplayName("从 RETRYING(5) 出发")
    class FromRetrying {

        @Test
        @DisplayName("✓ RETRYING(5) → SUCCESS(2) 允许")
        void shouldAllowTransitionFromRetryingToSuccess() {
            assertTrue(stateMachine.canTransition(
                    PaymentStatusConstant.RETRYING.getCode(),
                    PaymentStatusConstant.SUCCESS.getCode()));
        }

        @Test
        @DisplayName("✓ RETRYING(5) → FAIL(3) 允许")
        void shouldAllowTransitionFromRetryingToFail() {
            assertTrue(stateMachine.canTransition(
                    PaymentStatusConstant.RETRYING.getCode(),
                    PaymentStatusConstant.FAIL.getCode()));
        }

        @Test
        @DisplayName("✗ RETRYING(5) → WAITING_PAY(0) 禁止")
        void shouldRejectTransitionFromRetryingToWaitingPay() {
            assertFalse(stateMachine.canTransition(
                    PaymentStatusConstant.RETRYING.getCode(),
                    PaymentStatusConstant.WAITING_PAY.getCode()));
        }
    }

    // ==================== 终态：SUCCESS(2) ====================

    @Nested
    @DisplayName("从 SUCCESS(2)（终态）出发")
    class FromSuccess {

        @Test
        @DisplayName("✗ SUCCESS(2) → PROCESSING(1) 禁止")
        void shouldRejectTransitionFromSuccessToProcessing() {
            assertFalse(stateMachine.canTransition(
                    PaymentStatusConstant.SUCCESS.getCode(),
                    PaymentStatusConstant.PROCESSING.getCode()));
        }

        @Test
        @DisplayName("✗ SUCCESS(2) → FAIL(3) 禁止")
        void shouldRejectTransitionFromSuccessToFail() {
            assertFalse(stateMachine.canTransition(
                    PaymentStatusConstant.SUCCESS.getCode(),
                    PaymentStatusConstant.FAIL.getCode()));
        }

        @Test
        @DisplayName("✗ SUCCESS(2) → ANY 禁止（终态不可转移）")
        void shouldRejectAnyTransitionFromSuccess() {
            for (PaymentStatusConstant target : PaymentStatusConstant.values()) {
                assertFalse(stateMachine.canTransition(
                        PaymentStatusConstant.SUCCESS.getCode(), target.getCode()),
                        "SUCCESS should not transition to " + target.name());
            }
        }
    }

    // ==================== 终态：EXPIRED(4) ====================

    @Nested
    @DisplayName("从 EXPIRED(4)（终态）出发")
    class FromExpired {

        @Test
        @DisplayName("✗ EXPIRED(4) → PROCESSING(1) 禁止")
        void shouldRejectTransitionFromExpiredToProcessing() {
            assertFalse(stateMachine.canTransition(
                    PaymentStatusConstant.EXPIRED.getCode(),
                    PaymentStatusConstant.PROCESSING.getCode()));
        }

        @Test
        @DisplayName("✗ EXPIRED(4) → ANY 禁止（终态不可转移）")
        void shouldRejectAnyTransitionFromExpired() {
            for (PaymentStatusConstant target : PaymentStatusConstant.values()) {
                assertFalse(stateMachine.canTransition(
                        PaymentStatusConstant.EXPIRED.getCode(), target.getCode()),
                        "EXPIRED should not transition to " + target.name());
            }
        }
    }

    // ==================== null 初始状态 → WAITING_PAY ====================

    @Nested
    @DisplayName("从 null（新建支付）出发")
    class FromNull {

        @Test
        @DisplayName("✓ null → WAITING_PAY(0) 允许（新建支付）")
        void shouldAllowTransitionFromNullToWaitingPay() {
            assertTrue(stateMachine.canTransition(null, PaymentStatusConstant.WAITING_PAY.getCode()));
        }

        @Test
        @DisplayName("✗ null → PROCESSING(1) 禁止")
        void shouldRejectTransitionFromNullToProcessing() {
            assertFalse(stateMachine.canTransition(null, PaymentStatusConstant.PROCESSING.getCode()));
        }

        @Test
        @DisplayName("✗ null → SUCCESS(2) 禁止")
        void shouldRejectTransitionFromNullToSuccess() {
            assertFalse(stateMachine.canTransition(null, PaymentStatusConstant.SUCCESS.getCode()));
        }
    }

    // ==================== validateTransition 异常校验 ====================

    @Nested
    @DisplayName("validateTransition 异常测试")
    class ValidateTransition {

        @Test
        @DisplayName("✓ 合法转移不抛异常")
        void shouldNotThrowWhenTransitionIsValid() {
            assertDoesNotThrow(() -> stateMachine.validateTransition(
                    PaymentStatusConstant.WAITING_PAY.getCode(),
                    PaymentStatusConstant.PROCESSING.getCode()));
        }

        @Test
        @DisplayName("✗ 非法转移抛出 BusinessException，错误码 STATE_TRANSITION_INVALID")
        void shouldThrowBusinessExceptionWhenTransitionIsInvalid() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> stateMachine.validateTransition(
                            PaymentStatusConstant.WAITING_PAY.getCode(),
                            PaymentStatusConstant.SUCCESS.getCode()));
            assertEquals(PaymentErrorCode.STATE_TRANSITION_INVALID.getCode(), ex.getCode());
            assertTrue(ex.getMessage().contains(PaymentErrorCode.STATE_TRANSITION_INVALID.getMessage()));
            assertTrue(ex.getMessage().contains("→"));
        }

        @Test
        @DisplayName("✗ 终态转移抛出异常")
        void shouldThrowWhenTransitionFromFinalState() {
            assertThrows(BusinessException.class,
                    () -> stateMachine.validateTransition(
                            PaymentStatusConstant.SUCCESS.getCode(),
                            PaymentStatusConstant.PROCESSING.getCode()));
        }

        @Test
        @DisplayName("✗ null → 非 WAITING_PAY 抛出异常")
        void shouldThrowWhenNullToNonWaitingPay() {
            assertThrows(BusinessException.class,
                    () -> stateMachine.validateTransition(null, PaymentStatusConstant.SUCCESS.getCode()));
        }
    }
}
