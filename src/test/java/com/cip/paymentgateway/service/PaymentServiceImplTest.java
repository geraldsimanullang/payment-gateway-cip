package com.cip.paymentgateway.service;

import com.cip.paymentgateway.client.BillerAggregatorClient;
import com.cip.paymentgateway.client.CoreBankingClient;
import com.cip.paymentgateway.dto.biller.BillerRequest;
import com.cip.paymentgateway.dto.biller.BillerResponse;
import com.cip.paymentgateway.dto.corebanking.CoreBankingRequest;
import com.cip.paymentgateway.dto.corebanking.CoreBankingResponse;
import com.cip.paymentgateway.dto.request.PaymentRequest;
import com.cip.paymentgateway.dto.response.PaymentResponse;
import com.cip.paymentgateway.entity.Channel;
import com.cip.paymentgateway.entity.Transaction;
import com.cip.paymentgateway.entity.TransactionStatus;
import com.cip.paymentgateway.exception.DuplicateOrderException;
import com.cip.paymentgateway.exception.TransactionNotFoundException;
import com.cip.paymentgateway.kafka.PaymentEventProducer;
import com.cip.paymentgateway.repository.TransactionRepository;
import com.cip.paymentgateway.service.impl.PaymentServiceImpl;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CoreBankingClient coreBankingClient;

    @Mock
    private BillerAggregatorClient billerAggregatorClient;

    @Mock
    private PaymentEventProducer eventProducer;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private PaymentRequest paymentRequest;
    private Transaction savedTransaction;

    @BeforeEach
    void setUp() {
        // Data dummy yang dipakai di semua test
        paymentRequest = PaymentRequest.builder()
                .orderId("INV-001")
                .channel(Channel.MOBILE_BANKING)
                .amount(new BigDecimal("250000"))
                .account("1234567890")
                .currency("IDR")
                .paymentMethod("VIRTUAL_ACCOUNT")
                .build();

        savedTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .orderId("INV-001")
                .channel(Channel.MOBILE_BANKING)
                .amount(new BigDecimal("250000"))
                .account("1234567890")
                .currency("IDR")
                .paymentMethod("VIRTUAL_ACCOUNT")
                .status(TransactionStatus.PENDING)
                .build();
    }

    // =============================================
    // TEST: processPayment
    // =============================================

    @Test
    void processPayment_shouldReturnSuccess_whenAllServicesReturnSuccess() {
        // Given
        when(transactionRepository.existsByOrderId("INV-001"))
                .thenReturn(false);
        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(savedTransaction);
        when(coreBankingClient.debit(any(CoreBankingRequest.class)))
                .thenReturn(CoreBankingResponse.builder()
                        .corebankReference("CB-123")
                        .status("SUCCESS")
                        .build());
        when(billerAggregatorClient.pay(any(BillerRequest.class)))
                .thenReturn(BillerResponse.builder()
                        .billerReference("BILLER-456")
                        .status("SUCCESS")
                        .build());

        // When
        PaymentResponse response = paymentService.processPayment(paymentRequest);

        // Then
        assertEquals("SUCCESS", response.getStatus());
        assertNotNull(response.getTransactionId());
        assertEquals("INV-001", response.getOrderId());

        // Verifikasi kafka event dipublish
        verify(eventProducer, times(1)).publishSuccessEvent(any(Transaction.class));
    }

    @Test
    void processPayment_shouldThrowException_whenOrderIdAlreadyExists() {
        // Given
        when(transactionRepository.existsByOrderId("INV-001"))
                .thenReturn(true);

        // When & Then
        assertThrows(DuplicateOrderException.class, () ->
                paymentService.processPayment(paymentRequest)
        );

        // Verifikasi tidak ada call ke core banking
        verify(coreBankingClient, never()).debit(any());
    }

    @Test
    void processPayment_shouldReturnFailed_whenCoreBankingReturnFailed() {
        // Given
        when(transactionRepository.existsByOrderId("INV-001"))
                .thenReturn(false);
        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(savedTransaction);
        when(coreBankingClient.debit(any(CoreBankingRequest.class)))
                .thenReturn(CoreBankingResponse.builder()
                        .status("FAILED")
                        .build());

        // When
        PaymentResponse response = paymentService.processPayment(paymentRequest);

        // Then
        assertEquals("FAILED", response.getStatus());
        assertEquals("Insufficient balance", response.getMessage());

        // Verifikasi tidak ada call ke biller
        verify(billerAggregatorClient, never()).pay(any());

        // Verifikasi tidak ada kafka event
        verify(eventProducer, never()).publishSuccessEvent(any());
    }

    @Test
    void processPayment_shouldReturnFailed_whenBillerReturnFailed() {
        // Given
        when(transactionRepository.existsByOrderId("INV-001"))
                .thenReturn(false);
        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(savedTransaction);
        when(coreBankingClient.debit(any(CoreBankingRequest.class)))
                .thenReturn(CoreBankingResponse.builder()
                        .corebankReference("CB-123")
                        .status("SUCCESS")
                        .build());
        when(billerAggregatorClient.pay(any(BillerRequest.class)))
                .thenReturn(BillerResponse.builder()
                        .status("FAILED")
                        .build());

        // When
        PaymentResponse response = paymentService.processPayment(paymentRequest);

        // Then
        assertEquals("FAILED", response.getStatus());
        assertEquals("Biller payment failed", response.getMessage());

        // Verifikasi tidak ada kafka event
        verify(eventProducer, never()).publishSuccessEvent(any());
    }

    @Test
    void processPayment_shouldReturnFailed_whenFeignExceptionThrown() {
        // Given
        when(transactionRepository.existsByOrderId("INV-001"))
                .thenReturn(false);
        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(savedTransaction);
        when(coreBankingClient.debit(any(CoreBankingRequest.class)))
                .thenThrow(FeignException.class);

        // When
        PaymentResponse response = paymentService.processPayment(paymentRequest);

        // Then
        assertEquals("FAILED", response.getStatus());
        assertEquals("External service unavailable", response.getMessage());
    }

    // =============================================
    // TEST: getTransaction
    // =============================================

    @Test
    void getTransaction_shouldReturnTransaction_whenIdExists() {
        // Given
        UUID id = savedTransaction.getId();
        savedTransaction.setStatus(TransactionStatus.SUCCESS);
        savedTransaction.setCorebankReference("CB-123");
        savedTransaction.setBillerReference("BILLER-456");

        when(transactionRepository.findById(id))
                .thenReturn(Optional.of(savedTransaction));

        // When
        PaymentResponse response = paymentService.getTransaction(id);

        // Then
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("INV-001", response.getOrderId());
        assertEquals("CB-123", response.getCorebankReference());
        assertEquals("BILLER-456", response.getBillerReference());
    }

    @Test
    void getTransaction_shouldThrowException_whenIdNotFound() {
        // Given
        UUID id = UUID.randomUUID();
        when(transactionRepository.findById(id))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(TransactionNotFoundException.class, () ->
                paymentService.getTransaction(id)
        );
    }
}