package com.cip.paymentgateway.service.impl;

import com.cip.paymentgateway.client.BillerAggregatorClient;
import com.cip.paymentgateway.client.CoreBankingClient;
import com.cip.paymentgateway.dto.biller.BillerRequest;
import com.cip.paymentgateway.dto.biller.BillerResponse;
import com.cip.paymentgateway.dto.corebanking.CoreBankingRequest;
import com.cip.paymentgateway.dto.corebanking.CoreBankingResponse;
import com.cip.paymentgateway.dto.request.PaymentRequest;
import com.cip.paymentgateway.dto.response.PaymentResponse;
import com.cip.paymentgateway.entity.Transaction;
import com.cip.paymentgateway.entity.TransactionStatus;
import com.cip.paymentgateway.exception.DuplicateOrderException;
import com.cip.paymentgateway.exception.TransactionNotFoundException;
import com.cip.paymentgateway.kafka.PaymentEventProducer;
import com.cip.paymentgateway.repository.TransactionRepository;
import com.cip.paymentgateway.service.PaymentService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final TransactionRepository transactionRepository;
    private final CoreBankingClient coreBankingClient;
    private final BillerAggregatorClient billerAggregatorClient;
    private final PaymentEventProducer eventProducer;

    @Override
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {

        log.info("Processing payment for orderId: {}", request.getOrderId());

        // 1. Cek duplicate order
        if (transactionRepository.existsByOrderId(request.getOrderId())) {
            throw new DuplicateOrderException(
                    "Order already exists: " + request.getOrderId());
        }

        // 2. Simpan transaksi dengan status PENDING
        Transaction transaction = Transaction.builder()
                .orderId(request.getOrderId())
                .channel(request.getChannel())
                .amount(request.getAmount())
                .account(request.getAccount())
                .currency(request.getCurrency())
                .paymentMethod(request.getPaymentMethod())
                .status(TransactionStatus.PENDING)
                .build();

        transaction = transactionRepository.save(transaction);
        log.info("Transaction saved with id: {}", transaction.getId());

        try {
            // 3. Kirim debit request ke Core Banking
            log.info("Sending debit request to core banking for account: {}",
                    request.getAccount());

            CoreBankingResponse cbResponse = coreBankingClient.debit(
                    CoreBankingRequest.builder()
                            .account(request.getAccount())
                            .amount(request.getAmount())
                            .build());

            // 4. Cek response Core Banking
            if (!"SUCCESS".equals(cbResponse.getStatus())) {
                log.warn("Core banking returned failed status for orderId: {}",
                        request.getOrderId());
                return failTransaction(transaction, "Insufficient balance");
            }

            transaction.setCorebankReference(cbResponse.getCorebankReference());
            log.info("Core banking success, reference: {}",
                    cbResponse.getCorebankReference());

            // 5. Forward ke Biller Aggregator
            log.info("Forwarding payment to biller for orderId: {}",
                    request.getOrderId());

            BillerResponse billerResponse = billerAggregatorClient.pay(
                    BillerRequest.builder()
                            .orderId(request.getOrderId())
                            .amount(request.getAmount())
                            .paymentMethod(request.getPaymentMethod())
                            .build());

            // 6. Cek response Biller
            if (!"SUCCESS".equals(billerResponse.getStatus())) {
                log.warn("Biller returned failed status for orderId: {}",
                        request.getOrderId());
                return failTransaction(transaction, "Biller payment failed");
            }

            // 7. Update transaksi menjadi SUCCESS
            transaction.setBillerReference(billerResponse.getBillerReference());
            transaction.setStatus(TransactionStatus.SUCCESS);
            transactionRepository.save(transaction);

            // 8. Publish event ke Kafka
            eventProducer.publishSuccessEvent(transaction);

            log.info("Payment successful for orderId: {}", request.getOrderId());

            return PaymentResponse.builder()
                    .transactionId(transaction.getId().toString())
                    .orderId(transaction.getOrderId())
                    .status(TransactionStatus.SUCCESS.name())
                    .corebankReference(transaction.getCorebankReference())
                    .billerReference(transaction.getBillerReference())
                    .build();

        } catch (FeignException e) {
            log.error("External service error for orderId: {}, error: {}",
                    request.getOrderId(), e.getMessage());
            return failTransaction(transaction, "External service unavailable");
        }
    }

    @Override
    public PaymentResponse getTransaction(UUID id) {
        log.info("Getting transaction for id: {}", id);

        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction not found: " + id));

        return PaymentResponse.builder()
                .transactionId(transaction.getId().toString())
                .orderId(transaction.getOrderId())
                .status(transaction.getStatus().name())
                .corebankReference(transaction.getCorebankReference())
                .billerReference(transaction.getBillerReference())
                .build();
    }

    // Helper method untuk update transaksi menjadi FAILED
    private PaymentResponse failTransaction(Transaction transaction, String message) {
        transaction.setStatus(TransactionStatus.FAILED);
        transactionRepository.save(transaction);

        log.warn("Transaction failed for orderId: {}, reason: {}",
                transaction.getOrderId(), message);

        return PaymentResponse.builder()
                .transactionId(transaction.getId().toString())
                .orderId(transaction.getOrderId())
                .status(TransactionStatus.FAILED.name())
                .message(message)
                .build();
    }
}