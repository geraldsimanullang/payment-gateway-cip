package com.cip.paymentgateway.client;

import com.cip.paymentgateway.dto.biller.BillerRequest;
import com.cip.paymentgateway.dto.biller.BillerResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "biller-aggregator",
        url = "${biller.url}"
)
public interface BillerAggregatorClient {

    @PostMapping("/api/biller/pay")
    BillerResponse pay(@RequestBody BillerRequest request);
}