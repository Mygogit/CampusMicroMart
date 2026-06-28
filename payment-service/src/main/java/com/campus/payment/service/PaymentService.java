package com.campus.payment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.payment.dto.CreatePaymentDTO;
import com.campus.payment.entity.Payment;
import com.campus.payment.vo.PaymentVO;

public interface PaymentService extends IService<Payment> {
    Payment createPayment(CreatePaymentDTO createPaymentDTO);
    PaymentVO simulatePayment(Long paymentId);
    PaymentVO queryByOrderId(Long orderId);
}

