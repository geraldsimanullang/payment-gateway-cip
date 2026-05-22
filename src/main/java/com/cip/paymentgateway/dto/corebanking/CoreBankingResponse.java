package com.cip.paymentgateway.dto.corebanking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoreBankingResponse {

    private String corebankReference;
    private String status;
}