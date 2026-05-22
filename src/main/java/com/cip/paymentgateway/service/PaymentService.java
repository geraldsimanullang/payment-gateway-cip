package com.cip.paymentgateway.service;

import com.cip.paymentgateway.dto.request.PaymentRequest;
import com.cip.paymentgateway.dto.response.PaymentResponse;

import java.util.UUID;

public interface PaymentService {

    PaymentResponse processPayment(PaymentRequest request);

    PaymentResponse getTransaction(UUID id);
}