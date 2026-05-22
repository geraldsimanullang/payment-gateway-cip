package com.cip.paymentgateway.dto.corebanking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoreBankingRequest {

    private String account;
    private BigDecimal amount;
}