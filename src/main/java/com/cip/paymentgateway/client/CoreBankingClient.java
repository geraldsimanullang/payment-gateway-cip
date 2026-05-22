package com.cip.paymentgateway.client;

import com.cip.paymentgateway.dto.corebanking.CoreBankingRequest;
import com.cip.paymentgateway.dto.corebanking.CoreBankingResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "core-banking",
        url = "${corebanking.url}"
)
public interface CoreBankingClient {

    @PostMapping("/api/corebank/debit")
    CoreBankingResponse debit(@RequestBody CoreBankingRequest request);
}