package com.campus.payment.service;

import com.campus.common.constant.PaymentStatusConstant;
import com.campus.common.exception.BusinessException;
import com.campus.payment.constant.PaymentErrorCode;
import com.campus.payment.dto.CallbackRequest;
import com.campus.payment.dto.CallbackResponse;
import com.campus.payment.entity.Payment;
import com.campus.payment.mapper.PaymentMapper;
import com.campus.payment.service.impl.PaymentCallbackServiceImpl;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 支付回调处理服务单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("支付回调处理服务")
class PaymentCallbackServiceImplTest {

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private PaymentStateMachine paymentStateMachine;

    @InjectMocks
    private PaymentCallbackServiceImpl callbackService;

    private static final String PAYMENT_NO = "PAY-001";

    private Payment buildPayment(Integer status, String callbackStatus) {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setPaymentNo(PAYMENT_NO);
        payment.setUserId(1L);
        payment.setOrderId(100L);
        payment.setAmount(new BigDecimal("99.00"));
        payment.setStatus(status);
        payment.setCallbackStatus(callbackStatus);
        payment.setRetryCount(0);
        payment.setExpireTime(LocalDateTime.now().plusMinutes(10));
        return payment;
    }

    private CallbackRequest buildCallbackRequest(String callbackStatus, String sign, String failReason) {
        CallbackRequest request = new CallbackRequest();
        request.setPaymentNo(PAYMENT_NO);
        request.setCallbackStatus(callbackStatus);
        request.setSign(sign);
        request.setFailReason(failReason);
        request.setTimestamp(System.currentTimeMillis());
        return request;
    }

    @BeforeEach
    void setUp() {
        lenient().when(paymentMapper.updateById(any(Payment.class))).thenReturn(1);
    }

    // ==================== 合法回调 ====================

    @Nested
    @DisplayName("合法回调")
    class ValidCallback {

        @Test
        @DisplayName("✓ SUCCESS 回调：status 更新为 SUCCESS，callbackStatus=\"SUCCESS\"，callbackTime 设置")
        void shouldHandleSuccessCallback() {
            Payment payment = buildPayment(PaymentStatusConstant.PROCESSING.getCode(), null);
            when(paymentMapper.selectOne(any())).thenReturn(payment);

            // Allow PROCESSING → SUCCESS
            doNothing().when(paymentStateMachine).validateTransition(
                    eq(PaymentStatusConstant.PROCESSING.getCode()),
                    eq(PaymentStatusConstant.SUCCESS.getCode()));

            CallbackRequest request = buildCallbackRequest("SUCCESS", "mock_sign", null);
            CallbackResponse response = callbackService.handleCallback(request);

            assertEquals("200", response.getCode());
            assertEquals("success", response.getMessage());
            assertEquals(PAYMENT_NO, response.getPaymentNo());

            // 验证实体更新
            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentMapper).updateById(captor.capture());
            Payment updated = captor.getValue();
            assertEquals(PaymentStatusConstant.SUCCESS.getCode(), updated.getStatus());
            assertEquals("SUCCESS", updated.getCallbackStatus());
            assertNotNull(updated.getCallbackTime());
        }

        @Test
        @DisplayName("✓ FAIL 回调：status 更新为 FAIL，failReason 设置")
        void shouldHandleFailCallback() {
            Payment payment = buildPayment(PaymentStatusConstant.PROCESSING.getCode(), null);
            when(paymentMapper.selectOne(any())).thenReturn(payment);

            doNothing().when(paymentStateMachine).validateTransition(
                    eq(PaymentStatusConstant.PROCESSING.getCode()),
                    eq(PaymentStatusConstant.FAIL.getCode()));

            CallbackRequest request = buildCallbackRequest("FAIL", "mock_sign", "余额不足");
            CallbackResponse response = callbackService.handleCallback(request);

            assertEquals("200", response.getCode());

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentMapper).updateById(captor.capture());
            Payment updated = captor.getValue();
            assertEquals(PaymentStatusConstant.FAIL.getCode(), updated.getStatus());
            assertEquals("FAIL", updated.getCallbackStatus());
            assertEquals("余额不足", updated.getFailReason());
        }
    }

    // ==================== 异常场景 ====================

    @Nested
    @DisplayName("异常场景")
    class ErrorScenarios {

        @Test
        @DisplayName("✗ payNo 不存在：返回 CallbackResponse.fail，错误码 40001")
        void shouldReturnFailWhenPaymentNotFound() {
            when(paymentMapper.selectOne(any())).thenReturn(null);

            CallbackRequest request = buildCallbackRequest("SUCCESS", "mock_sign", null);
            CallbackResponse response = callbackService.handleCallback(request);

            assertEquals(String.valueOf(PaymentErrorCode.PAYMENT_NOT_FOUND.getCode()), response.getCode());
            assertEquals(PaymentErrorCode.PAYMENT_NOT_FOUND.getMessage(), response.getMessage());
            assertEquals(PAYMENT_NO, response.getPaymentNo());
        }

        @Test
        @DisplayName("✓ 幂等性：已回调过直接返回成功，不更新数据库")
        void shouldReturnSuccessWhenAlreadyProcessed() {
            Payment payment = buildPayment(PaymentStatusConstant.SUCCESS.getCode(), "SUCCESS");
            when(paymentMapper.selectOne(any())).thenReturn(payment);

            CallbackRequest request = buildCallbackRequest("SUCCESS", "mock_sign", null);
            CallbackResponse response = callbackService.handleCallback(request);

            assertEquals("200", response.getCode());
            assertEquals("success", response.getMessage());
            // 幂等场景不应更新数据库
            verify(paymentMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("✗ 无效签名：返回错误码 40011，签名校验失败")
        void shouldReturnFailWhenInvalidSign() {
            CallbackRequest request = buildCallbackRequest("SUCCESS", "invalid_sign", null);
            CallbackResponse response = callbackService.handleCallback(request);

            assertEquals("40011", response.getCode());
            assertTrue(response.getMessage().contains("签名校验失败"));
        }

        @Test
        @DisplayName("✗ 终态支付回调：SUCCESS 状态再次回调被状态机拒绝，返回 40008")
        void shouldRejectCallbackOnTerminalState() {
            Payment payment = buildPayment(PaymentStatusConstant.SUCCESS.getCode(), null);
            when(paymentMapper.selectOne(any())).thenReturn(payment);

            doThrow(new BusinessException(
                    PaymentErrorCode.STATE_TRANSITION_INVALID.getCode(),
                    PaymentErrorCode.STATE_TRANSITION_INVALID.getMessage()))
                    .when(paymentStateMachine).validateTransition(
                            eq(PaymentStatusConstant.SUCCESS.getCode()),
                            eq(PaymentStatusConstant.SUCCESS.getCode()));

            CallbackRequest request = buildCallbackRequest("SUCCESS", "mock_sign", null);
            CallbackResponse response = callbackService.handleCallback(request);

            assertEquals(String.valueOf(PaymentErrorCode.STATE_TRANSITION_INVALID.getCode()), response.getCode());
            assertTrue(response.getMessage().contains(PaymentErrorCode.STATE_TRANSITION_INVALID.getMessage()));
        }
    }

    // ==================== 边界场景 ====================

    @Nested
    @DisplayName("边界场景")
    class EdgeCases {

        @Test
        @DisplayName("✓ FAIL 回调无 failReason 时不设置 failReason")
        void shouldNotSetFailReasonWhenNull() {
            Payment payment = buildPayment(PaymentStatusConstant.PROCESSING.getCode(), null);
            when(paymentMapper.selectOne(any())).thenReturn(payment);

            doNothing().when(paymentStateMachine).validateTransition(
                    eq(PaymentStatusConstant.PROCESSING.getCode()),
                    eq(PaymentStatusConstant.FAIL.getCode()));

            CallbackRequest request = buildCallbackRequest("FAIL", "mock_sign", null);
            CallbackResponse response = callbackService.handleCallback(request);

            assertEquals("200", response.getCode());

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentMapper).updateById(captor.capture());
            // failReason should remain null (request had null failReason)
            assertNull(captor.getValue().getFailReason());
        }
    }
}
