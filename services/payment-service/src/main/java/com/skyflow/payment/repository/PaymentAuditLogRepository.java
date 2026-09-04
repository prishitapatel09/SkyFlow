package com.skyflow.payment.repository;

import com.skyflow.payment.domain.PaymentAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAuditLogRepository extends JpaRepository<PaymentAuditLog, Long> {
}
