package com.cip.paymentgateway.kafka;

import com.cip.paymentgateway.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

    private static final String TOPIC = "transaction.success";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishSuccessEvent(Transaction transaction) {

        PaymentSuccessEvent event = PaymentSuccessEvent.builder()
                .transactionId(transaction.getId().toString())
                .orderId(transaction.getOrderId())
                .account(transaction.getAccount())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .corebankReference(transaction.getCorebankReference())
                .billerReference(transaction.getBillerReference())
                .timestamp(LocalDateTime.now())
                .build();

        // Kirim event secara async
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC, transaction.getOrderId(), event);

        // Handle hasil pengiriman
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event for orderId: {}, error: {}",
                        transaction.getOrderId(), ex.getMessage());
            } else {
                log.info("Event published successfully for orderId: {}, offset: {}",
                        transaction.getOrderId(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}