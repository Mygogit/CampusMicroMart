package com.campus.payment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.payment.dto.CreatePaymentDTO;
import com.campus.payment.entity.Payment;

public interface PaymentService extends IService<Payment> {
    Payment createPayment(CreatePaymentDTO createPaymentDTO);
    boolean simulatePayment(Long paymentId);
}

