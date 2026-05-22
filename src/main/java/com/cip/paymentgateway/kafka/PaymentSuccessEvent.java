package com.cip.paymentgateway.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSuccessEvent {

    private String transactionId;
    private String orderId;
    private String account;
    private BigDecimal amount;
    private String currency;
    private String corebankReference;
    private String billerReference;
    private LocalDateTime timestamp;
}