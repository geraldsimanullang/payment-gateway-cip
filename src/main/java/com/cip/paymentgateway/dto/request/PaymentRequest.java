package com.cip.paymentgateway.dto.request;

import com.cip.paymentgateway.entity.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotBlank(message = "orderId is required")
    private String orderId;

    @NotNull(message = "channel is required")
    private Channel channel;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "account is required")
    private String account;

    @NotBlank(message = "currency is required")
    private String currency;

    @NotBlank(message = "paymentMethod is required")
    private String paymentMethod;
}