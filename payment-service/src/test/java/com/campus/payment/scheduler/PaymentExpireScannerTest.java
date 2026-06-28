package com.campus.payment.scheduler;

import com.campus.common.constant.PaymentStatusConstant;
import com.campus.common.exception.BusinessException;
import com.campus.payment.constant.PaymentErrorCode;
import com.campus.payment.entity.Payment;
import com.campus.payment.mapper.PaymentMapper;
import com.campus.payment.statemachine.PaymentStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 过期支付定时扫描器单元测试
 * <p>
 * 模拟 PaymentMapper 和 PaymentStateMachine，验证过期扫描逻辑。
 * StringRedisTemplate 保持 null（required=false），doScan() 直接执行。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("过期支付定时扫描器")
class PaymentExpireScannerTest {

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private PaymentStateMachine paymentStateMachine;

    @InjectMocks
    private PaymentExpireScanner scanner;

    private Payment buildPayment(Long id, Integer status, LocalDateTime expireTime) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setPaymentNo("PAY-" + String.format("%03d", id));
        payment.setUserId(1L);
        payment.setOrderId(100L + id);
        payment.setAmount(new BigDecimal("99.00"));
        payment.setStatus(status);
        payment.setRetryCount(0);
        payment.setExpireTime(expireTime);
        return payment;
    }

    @BeforeEach
    void setUp() {
        lenient().when(paymentMapper.updateById(any(Payment.class))).thenReturn(1);
        // Allow WAITING_PAY → EXPIRED and PROCESSING → EXPIRED by default
        lenient().doNothing().when(paymentStateMachine)
                .validateTransition(anyInt(), eq(PaymentStatusConstant.EXPIRED.getCode()));
    }

    // ==================== 正常扫描 ====================

    @Nested
    @DisplayName("正常过期扫描")
    class NormalScan {

        @Test
        @DisplayName("✓ 扫描到 WAITING_PAY 过期支付 → 更新为 EXPIRED")
        void shouldExpireWaitingPayPayments() {
            Payment expired = buildPayment(1L, PaymentStatusConstant.WAITING_PAY.getCode(),
                    LocalDateTime.now().minusMinutes(1));
            when(paymentMapper.selectList(any())).thenReturn(List.of(expired));

            scanner.scanExpiredPayments();

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentMapper).updateById(captor.capture());
            assertEquals(PaymentStatusConstant.EXPIRED.getCode(), captor.getValue().getStatus());
        }

        @Test
        @DisplayName("✓ 扫描到 PROCESSING 过期支付 → 更新为 EXPIRED")
        void shouldExpireProcessingPayments() {
            Payment expired = buildPayment(2L, PaymentStatusConstant.PROCESSING.getCode(),
                    LocalDateTime.now().minusMinutes(1));
            when(paymentMapper.selectList(any())).thenReturn(List.of(expired));

            scanner.scanExpiredPayments();

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentMapper).updateById(captor.capture());
            assertEquals(PaymentStatusConstant.EXPIRED.getCode(), captor.getValue().getStatus());
        }

        @Test
        @DisplayName("✓ 多笔过期支付全部处理")
        void shouldExpireMultiplePayments() {
            Payment p1 = buildPayment(1L, PaymentStatusConstant.WAITING_PAY.getCode(),
                    LocalDateTime.now().minusMinutes(5));
            Payment p2 = buildPayment(2L, PaymentStatusConstant.PROCESSING.getCode(),
                    LocalDateTime.now().minusMinutes(3));
            when(paymentMapper.selectList(any())).thenReturn(List.of(p1, p2));

            scanner.scanExpiredPayments();

            verify(paymentMapper, times(2)).updateById(any(Payment.class));
        }
    }

    // ==================== 未过期 + 非目标状态 ====================

    @Nested
    @DisplayName("未过期与跳过")
    class SkipScenarios {

        @Test
        @DisplayName("✓ 未过期的支付不被扫描（expireTime > now）")
        void shouldNotScanNonExpiredPayments() {
            // selectList returns empty because expireTime > now
            when(paymentMapper.selectList(any())).thenReturn(Collections.emptyList());

            scanner.scanExpiredPayments();

            verify(paymentMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("✗ SUCCESS 状态的支付不被扫描（不在查询条件中）")
        void shouldNotScanSuccessPayments() {
            // The query only selects WAITING_PAY and PROCESSING, so SUCCESS won't appear
            when(paymentMapper.selectList(any())).thenReturn(Collections.emptyList());

            scanner.scanExpiredPayments();

            verify(paymentMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("✗ FAIL 状态的支付不被扫描")
        void shouldNotScanFailPayments() {
            when(paymentMapper.selectList(any())).thenReturn(Collections.emptyList());

            scanner.scanExpiredPayments();

            verify(paymentMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("✗ EXPIRED 状态的支付不被扫描")
        void shouldNotScanAlreadyExpiredPayments() {
            when(paymentMapper.selectList(any())).thenReturn(Collections.emptyList());

            scanner.scanExpiredPayments();

            verify(paymentMapper, never()).updateById(any());
        }
    }

    // ==================== 异常容错 ====================

    @Nested
    @DisplayName("异常容错")
    class FaultTolerance {

        @Test
        @DisplayName("✓ 单条异常不影响其他记录")
        void shouldContinueOnSingleFailure() {
            Payment p1 = buildPayment(1L, PaymentStatusConstant.WAITING_PAY.getCode(),
                    LocalDateTime.now().minusMinutes(5));
            Payment p2 = buildPayment(2L, PaymentStatusConstant.WAITING_PAY.getCode(),
                    LocalDateTime.now().minusMinutes(5));
            Payment p3 = buildPayment(3L, PaymentStatusConstant.WAITING_PAY.getCode(),
                    LocalDateTime.now().minusMinutes(5));
            when(paymentMapper.selectList(any())).thenReturn(List.of(p1, p2, p3));

            // p1 正常，p2 的 updateById 抛 BusinessException，p3 正常
            lenient().when(paymentMapper.updateById(p1)).thenReturn(1);
            when(paymentMapper.updateById(p2)).thenThrow(new BusinessException("DB error"));
            lenient().when(paymentMapper.updateById(p3)).thenReturn(1);

            scanner.scanExpiredPayments();

            // p1 和 p3 应被更新
            verify(paymentMapper, times(1)).updateById(p1);
            verify(paymentMapper, times(1)).updateById(p2); // 尝试过但失败
            verify(paymentMapper, times(1)).updateById(p3);
        }

        @Test
        @DisplayName("✓ 状态转换被 BusinessException 拒绝时跳过该条")
        void shouldSkipWhenTransitionRejected() {
            Payment payment = buildPayment(1L, PaymentStatusConstant.WAITING_PAY.getCode(),
                    LocalDateTime.now().minusMinutes(5));
            when(paymentMapper.selectList(any())).thenReturn(List.of(payment));

            doThrow(new BusinessException(
                    PaymentErrorCode.STATE_TRANSITION_INVALID.getCode(),
                    PaymentErrorCode.STATE_TRANSITION_INVALID.getMessage()))
                    .when(paymentStateMachine).validateTransition(
                            eq(PaymentStatusConstant.WAITING_PAY.getCode()),
                            eq(PaymentStatusConstant.EXPIRED.getCode()));

            // 不应抛异常
            assertDoesNotThrow(() -> scanner.scanExpiredPayments());
            // 状态转换失败后不应调用 updateById
            verify(paymentMapper, never()).updateById(any());
        }
    }
}
