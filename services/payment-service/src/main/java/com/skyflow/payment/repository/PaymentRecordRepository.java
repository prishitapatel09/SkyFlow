package com.skyflow.payment.repository;

import com.skyflow.payment.domain.PaymentRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    Optional<PaymentRecord> findByPaymentIntentId(String paymentIntentId);

    List<PaymentRecord> findByCustomerIdOrderByCreatedAtDesc(String customerId, Pageable pageable);
}
