package com.campus.payment.consumer;

import com.campus.common.constant.PaymentStatusConstant;
import com.campus.payment.config.RetryProperties;
import com.campus.payment.entity.Payment;
import com.campus.payment.mapper.PaymentMapper;
import com.campus.payment.statemachine.PaymentStateMachine;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 支付重试处理器单元测试
 * <p>
 * 使用反射测试私有方法 simulateRetry、calcDelayLevel、mapSecondsToDelayLevel。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("支付重试处理器")
class PaymentRetryProcessorTest {

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private PaymentStateMachine paymentStateMachine;

    @Mock
    private RetryProperties retryProperties;

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @InjectMocks
    private PaymentRetryProcessor processor;

    private RetryProperties.Retry retryConfig;
    private RetryProperties.Simulate simulateConfig;

    private static final Long PAYMENT_ID = 1L;

    private Payment buildPayment(Long id, Integer status, int retryCount) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setPaymentNo("PAY-" + String.format("%03d", id));
        payment.setUserId(1L);
        payment.setOrderId(100L);
        payment.setAmount(new BigDecimal("99.00"));
        payment.setStatus(status);
        payment.setRetryCount(retryCount);
        payment.setExpireTime(LocalDateTime.now().plusMinutes(10));
        return payment;
    }

    @BeforeEach
    void setUp() {
        retryConfig = new RetryProperties.Retry();
        retryConfig.setMaxRetries(3);
        retryConfig.setIntervals(List.of(3000L, 10000L, 30000L));

        simulateConfig = new RetryProperties.Simulate();
        simulateConfig.setFailRate(0.3);

        lenient().when(retryProperties.getRetry()).thenReturn(retryConfig);
        lenient().when(retryProperties.getSimulate()).thenReturn(simulateConfig);
        lenient().when(paymentMapper.updateById(any(Payment.class))).thenReturn(1);
    }

    // ==================== onMessage ====================

    @Nested
    @DisplayName("onMessage")
    class OnMessage {

        @Test
        @DisplayName("✓ FAIL 状态，retryCount < max → 状态先流转 RETRYING，再 simulate")
        void shouldTransitionToRetryingAndSimulate() {
            simulateConfig.setFailRate(0.0); // 100% success
            Payment payment = buildPayment(PAYMENT_ID, PaymentStatusConstant.FAIL.getCode(), 0);
            when(paymentMapper.selectById(PAYMENT_ID)).thenReturn(payment);

            // Allow FAIL → RETRYING
            doNothing().when(paymentStateMachine).validateTransition(
                    eq(PaymentStatusConstant.FAIL.getCode()),
                    eq(PaymentStatusConstant.RETRYING.getCode()));
            // Allow RETRYING → SUCCESS
            doNothing().when(paymentStateMachine).validateTransition(
                    eq(PaymentStatusConstant.RETRYING.getCode()),
                    eq(PaymentStatusConstant.SUCCESS.getCode()));

            processor.onMessage(String.valueOf(PAYMENT_ID));

            // Verify: RETRYING status update + SUCCESS status update
            verify(paymentMapper, atLeast(2)).updateById(any(Payment.class));
        }

        @Test
        @DisplayName("✓ simulateRetry 成功 → 最终 SUCCESS")
        void shouldSetSuccessOnRetrySuccess() {
            simulateConfig.setFailRate(0.0);
            Payment payment = buildPayment(PAYMENT_ID, PaymentStatusConstant.FAIL.getCode(), 0);
            when(paymentMapper.selectById(PAYMENT_ID)).thenReturn(payment);

            doNothing().when(paymentStateMachine).validateTransition(
                    eq(PaymentStatusConstant.FAIL.getCode()),
                    eq(PaymentStatusConstant.RETRYING.getCode()));
            doNothing().when(paymentStateMachine).validateTransition(
                    eq(PaymentStatusConstant.RETRYING.getCode()),
                    eq(PaymentStatusConstant.SUCCESS.getCode()));

            processor.onMessage(String.valueOf(PAYMENT_ID));

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentMapper, atLeast(2)).updateById(captor.capture());
            // Last update should have SUCCESS status
            Payment lastUpdate = captor.getAllValues().get(captor.getAllValues().size() - 1);
            assertEquals(PaymentStatusConstant.SUCCESS.getCode(), lastUpdate.getStatus());
            assertEquals("SUCCESS", lastUpdate.getCallbackStatus());
        }

        @Test
        @DisplayName("✓ simulateRetry 失败 → 最终 FAIL，retryCount 递增，发送下一条重试消息")
        void shouldIncrementRetryCountAndResendOnFailure() {
            simulateConfig.setFailRate(1.0); // 100% failure
            Payment payment = buildPayment(PAYMENT_ID, PaymentStatusConstant.FAIL.getCode(), 0);
            when(paymentMapper.selectById(PAYMENT_ID)).thenReturn(payment);

            doNothing().when(paymentStateMachine).validateTransition(
                    eq(PaymentStatusConstant.FAIL.getCode()),
                    eq(PaymentStatusConstant.RETRYING.getCode()));
            doNothing().when(paymentStateMachine).validateTransition(
                    eq(PaymentStatusConstant.RETRYING.getCode()),
                    eq(PaymentStatusConstant.FAIL.getCode()));

            processor.onMessage(String.valueOf(PAYMENT_ID));

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentMapper, atLeast(2)).updateById(captor.capture());
            Payment lastUpdate = captor.getAllValues().get(captor.getAllValues().size() - 1);
            assertEquals(PaymentStatusConstant.FAIL.getCode(), lastUpdate.getStatus());
            assertEquals(1, lastUpdate.getRetryCount());
            assertTrue(lastUpdate.getFailReason().contains("第1次重试失败"));

            // 应发送下一条重试延迟消息
            verify(rocketMQTemplate, times(1))
                    .syncSend(contains("payment-retry-topic"), any(), eq(3000L), anyInt());
        }

        @Test
        @DisplayName("✓ retryCount 已达上限 → 不重试，直接标记最终失败")
        void shouldNotRetryWhenMaxRetriesReached() {
            Payment payment = buildPayment(PAYMENT_ID, PaymentStatusConstant.FAIL.getCode(), 3);
            when(paymentMapper.selectById(PAYMENT_ID)).thenReturn(payment);

            processor.onMessage(String.valueOf(PAYMENT_ID));

            // Should update failReason
            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentMapper).updateById(captor.capture());
            assertTrue(captor.getValue().getFailReason().contains("重试3次后仍失败"));

            // Should NOT attempt state transition or simulate
            verify(paymentStateMachine, never()).validateTransition(anyInt(), anyInt());
            verify(rocketMQTemplate, never()).syncSend(anyString(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("✗ 非 FAIL 状态支付 → 跳过不处理")
        void shouldSkipNonFailPayments() {
            Payment payment = buildPayment(PAYMENT_ID, PaymentStatusConstant.WAITING_PAY.getCode(), 0);
            when(paymentMapper.selectById(PAYMENT_ID)).thenReturn(payment);

            processor.onMessage(String.valueOf(PAYMENT_ID));

            verify(paymentMapper, never()).updateById(any());
            verify(paymentStateMachine, never()).validateTransition(anyInt(), anyInt());
        }

        @Test
        @DisplayName("✗ payId 不存在 → 跳过不处理")
        void shouldSkipNonExistentPayment() {
            when(paymentMapper.selectById(PAYMENT_ID)).thenReturn(null);

            processor.onMessage(String.valueOf(PAYMENT_ID));

            verify(paymentMapper, never()).updateById(any());
            verify(paymentStateMachine, never()).validateTransition(anyInt(), anyInt());
        }

        @Test
        @DisplayName("✓ PROCESSING 状态支付被跳过")
        void shouldSkipProcessingPayments() {
            Payment payment = buildPayment(PAYMENT_ID, PaymentStatusConstant.PROCESSING.getCode(), 0);
            when(paymentMapper.selectById(PAYMENT_ID)).thenReturn(payment);

            processor.onMessage(String.valueOf(PAYMENT_ID));

            verify(paymentMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("✓ SUCCESS 状态支付被跳过")
        void shouldSkipSuccessPayments() {
            Payment payment = buildPayment(PAYMENT_ID, PaymentStatusConstant.SUCCESS.getCode(), 0);
            when(paymentMapper.selectById(PAYMENT_ID)).thenReturn(payment);

            processor.onMessage(String.valueOf(PAYMENT_ID));

            verify(paymentMapper, never()).updateById(any());
        }
    }

    // ==================== calcDelayLevel (via reflection) ====================

    @Nested
    @DisplayName("calcDelayLevel 延迟级别计算")
    class CalcDelayLevel {

        /**
         * Helper: invoke private calcDelayLevel via reflection
         */
        private int invokeCalcDelayLevel(int retryCount) throws Exception {
            Method method = PaymentRetryProcessor.class.getDeclaredMethod("calcDelayLevel", int.class);
            method.setAccessible(true);
            return (int) method.invoke(processor, retryCount);
        }

        @Test
        @DisplayName("✓ retryCount=1 → intervals[0]=3s → delayLevel=2 (≤5s)")
        void shouldMap3sToLevel2() throws Exception {
            retryConfig.setIntervals(List.of(3000L));
            assertEquals(2, invokeCalcDelayLevel(1));
        }

        @Test
        @DisplayName("✓ retryCount=2 → intervals[1]=10s → delayLevel=3 (≤10s)")
        void shouldMap10sToLevel3() throws Exception {
            retryConfig.setIntervals(List.of(3000L, 10000L));
            assertEquals(3, invokeCalcDelayLevel(2));
        }

        @Test
        @DisplayName("✓ retryCount=3 → intervals[2]=30s → delayLevel=4 (≤30s)")
        void shouldMap30sToLevel4() throws Exception {
            retryConfig.setIntervals(List.of(3000L, 10000L, 30000L));
            assertEquals(4, invokeCalcDelayLevel(3));
        }

        @Test
        @DisplayName("✓ retryCount 超出配置索引 → 使用最后一个值")
        void shouldUseLastIntervalWhenBeyondConfig() throws Exception {
            retryConfig.setIntervals(List.of(3000L, 10000L, 30000L));
            int level1 = invokeCalcDelayLevel(4);
            int level2 = invokeCalcDelayLevel(5);
            assertEquals(level1, level2); // 超出后使用同一间隔
        }
    }

    // ==================== mapSecondsToDelayLevel (via reflection) ====================

    @Nested
    @DisplayName("mapSecondsToDelayLevel 秒数映射")
    class MapSecondsToDelayLevel {

        private int invokeMapSecondsToDelayLevel(int seconds) throws Exception {
            Method method = PaymentRetryProcessor.class.getDeclaredMethod("mapSecondsToDelayLevel", int.class);
            method.setAccessible(true);
            return (int) method.invoke(processor, seconds);
        }

        @Test
        @DisplayName("✓ ≤1s → delayLevel=1")
        void shouldMap1sToLevel1() throws Exception {
            assertEquals(1, invokeMapSecondsToDelayLevel(1));
        }

        @Test
        @DisplayName("✓ ≤5s → delayLevel=2")
        void shouldMap5sToLevel2() throws Exception {
            assertEquals(2, invokeMapSecondsToDelayLevel(3));
            assertEquals(2, invokeMapSecondsToDelayLevel(5));
        }

        @Test
        @DisplayName("✓ ≤10s → delayLevel=3")
        void shouldMap10sToLevel3() throws Exception {
            assertEquals(3, invokeMapSecondsToDelayLevel(6));
            assertEquals(3, invokeMapSecondsToDelayLevel(10));
        }

        @Test
        @DisplayName("✓ ≤30s → delayLevel=4")
        void shouldMap30sToLevel4() throws Exception {
            assertEquals(4, invokeMapSecondsToDelayLevel(11));
            assertEquals(4, invokeMapSecondsToDelayLevel(30));
        }

        @Test
        @DisplayName("✓ ≤60s → delayLevel=5")
        void shouldMap60sToLevel5() throws Exception {
            assertEquals(5, invokeMapSecondsToDelayLevel(31));
            assertEquals(5, invokeMapSecondsToDelayLevel(60));
        }

        @Test
        @DisplayName("✓ ≤120s → delayLevel=6")
        void shouldMap120sToLevel6() throws Exception {
            assertEquals(6, invokeMapSecondsToDelayLevel(61));
            assertEquals(6, invokeMapSecondsToDelayLevel(120));
        }

        @Test
        @DisplayName("✓ 最大值 >3600s → delayLevel=18")
        void shouldMapMaxToLevel18() throws Exception {
            assertEquals(18, invokeMapSecondsToDelayLevel(3601));
            assertEquals(18, invokeMapSecondsToDelayLevel(7200));
        }
    }

    // ==================== simulateRetry 状态转换异常 ====================

    @Nested
    @DisplayName("simulateRetry 状态转换异常")
    class SimulateRetryException {

        @Test
        @DisplayName("✓ 状态转换异常时被捕获，不抛到 onMessage 层")
        void shouldCatchTransitionExceptionInOnMessage() {
            Payment payment = buildPayment(PAYMENT_ID, PaymentStatusConstant.FAIL.getCode(), 0);
            when(paymentMapper.selectById(PAYMENT_ID)).thenReturn(payment);

            // FAIL → RETRYING throws BusinessException
            doThrow(new com.campus.common.exception.BusinessException("状态转换失败"))
                    .when(paymentStateMachine).validateTransition(
                            eq(PaymentStatusConstant.FAIL.getCode()),
                            eq(PaymentStatusConstant.RETRYING.getCode()));

            // Should not throw
            assertDoesNotThrow(() -> processor.onMessage(String.valueOf(PAYMENT_ID)));

            // Only one update for the RETRYING attempt (failed before update)
            // Actually, the code updates to RETRYING status first, then calls simulateRetry.
            // Wait, let me re-read the code...
            // In onMessage:
            // 1. validateTransition FAIL→RETRYING (throws here)
            // 2. payment.setStatus(RETRYING)
            // 3. paymentMapper.updateById(payment)
            // 4. simulateRetry(payment)
            // Since step 1 throws and is caught, steps 2-4 never execute.
            // So updateById is never called.
            verify(paymentMapper, never()).updateById(any());
        }
    }
}
