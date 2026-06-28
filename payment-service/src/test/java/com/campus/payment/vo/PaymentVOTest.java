package com.campus.payment.vo;

import com.campus.common.constant.PaymentStatusConstant;
import com.campus.payment.entity.Payment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PaymentVO 单元测试
 */
@DisplayName("PaymentVO 视图对象")
class PaymentVOTest {

    private Payment createPayment(Integer status, LocalDateTime expireTime) {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setPaymentNo("PAY-001");
        payment.setOrderId(100L);
        payment.setAmount(new BigDecimal("99.00"));
        payment.setStatus(status);
        payment.setRetryCount(2);
        payment.setFailReason("test reason");
        payment.setExpireTime(expireTime);
        payment.setCreateTime(LocalDateTime.of(2025, 1, 1, 10, 0, 0));
        return payment;
    }

    @Nested
    @DisplayName("from(Payment) 字段映射")
    class FromMapping {

        @Test
        @DisplayName("✓ 基本字段正确映射")
        void shouldMapBasicFieldsCorrectly() {
            Payment payment = createPayment(PaymentStatusConstant.WAITING_PAY.getCode(),
                    LocalDateTime.now().plusMinutes(10));
            PaymentVO vo = PaymentVO.from(payment);

            assertEquals(1L, vo.getId());
            assertEquals("PAY-001", vo.getPaymentNo());
            assertEquals(100L, vo.getOrderId());
            assertEquals(new BigDecimal("99.00"), vo.getAmount());
            assertEquals(PaymentStatusConstant.WAITING_PAY.getCode(), vo.getStatus());
            assertEquals(2, vo.getRetryCount());
            assertEquals("test reason", vo.getFailReason());
        }
    }

    @Nested
    @DisplayName("statusText 状态文本映射")
    class StatusTextMapping {

        @Test
        @DisplayName("✓ status=0 → \"待支付\"")
        void shouldMapWaitingPayText() {
            Payment payment = createPayment(0, LocalDateTime.now().plusMinutes(10));
            PaymentVO vo = PaymentVO.from(payment);
            assertEquals("待支付", vo.getStatusText());
        }

        @Test
        @DisplayName("✓ status=1 → \"处理中\"")
        void shouldMapProcessingText() {
            Payment payment = createPayment(1, LocalDateTime.now().plusMinutes(10));
            PaymentVO vo = PaymentVO.from(payment);
            assertEquals("处理中", vo.getStatusText());
        }

        @Test
        @DisplayName("✓ status=2 → \"支付成功\"")
        void shouldMapSuccessText() {
            Payment payment = createPayment(2, LocalDateTime.now().plusMinutes(10));
            PaymentVO vo = PaymentVO.from(payment);
            assertEquals("支付成功", vo.getStatusText());
        }

        @Test
        @DisplayName("✓ status=3 → \"支付失败\"")
        void shouldMapFailText() {
            Payment payment = createPayment(3, LocalDateTime.now().plusMinutes(10));
            PaymentVO vo = PaymentVO.from(payment);
            assertEquals("支付失败", vo.getStatusText());
        }

        @Test
        @DisplayName("✓ status=4 → \"已过期\"")
        void shouldMapExpiredText() {
            Payment payment = createPayment(4, LocalDateTime.now().plusMinutes(10));
            PaymentVO vo = PaymentVO.from(payment);
            assertEquals("已过期", vo.getStatusText());
        }

        @Test
        @DisplayName("✓ status=5 → \"重试中\"")
        void shouldMapRetryingText() {
            Payment payment = createPayment(5, LocalDateTime.now().plusMinutes(10));
            PaymentVO vo = PaymentVO.from(payment);
            assertEquals("重试中", vo.getStatusText());
        }

        @Test
        @DisplayName("✓ 未知状态码 → \"未知状态\"")
        void shouldMapUnknownStatusText() {
            Payment payment = createPayment(99, LocalDateTime.now().plusMinutes(10));
            PaymentVO vo = PaymentVO.from(payment);
            assertEquals("未知状态", vo.getStatusText());
        }
    }

    @Nested
    @DisplayName("remainingSeconds 剩余秒数计算")
    class RemainingSeconds {

        @Test
        @DisplayName("✓ 未来过期时间 → 正数秒数")
        void shouldCalculatePositiveRemainingSeconds() {
            Payment payment = createPayment(0, LocalDateTime.now().plusSeconds(300));
            PaymentVO vo = PaymentVO.from(payment);
            assertTrue(vo.getRemainingSeconds() > 0);
            assertTrue(vo.getRemainingSeconds() <= 300);
        }

        @Test
        @DisplayName("✓ 已过期 → remainingSeconds = 0")
        void shouldReturnZeroWhenExpired() {
            Payment payment = createPayment(0, LocalDateTime.now().minusMinutes(1));
            PaymentVO vo = PaymentVO.from(payment);
            assertEquals(0L, vo.getRemainingSeconds());
        }

        @Test
        @DisplayName("✓ expireTime=null → remainingSeconds = 0")
        void shouldReturnZeroWhenExpireTimeIsNull() {
            Payment payment = createPayment(0, null);
            PaymentVO vo = PaymentVO.from(payment);
            assertEquals(0L, vo.getRemainingSeconds());
        }
    }
}
