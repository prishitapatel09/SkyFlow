package com.skyflow.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyflow.payment.domain.PaymentAuditLog;
import com.skyflow.payment.repository.PaymentAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** Writes the payment audit trail. */
@Service
public class PaymentAuditService {

    private static final Logger log = LoggerFactory.getLogger(PaymentAuditService.class);

    private final PaymentAuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public PaymentAuditService(PaymentAuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Runs in its own transaction so the trail survives a rollback of the operation that logged it -
     * a failed refund is exactly the kind of thing you want a record of.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String eventType, String paymentIntentId, Map<String, ?> details) {
        try {
            repository.save(PaymentAuditLog.of(eventType, paymentIntentId,
                    objectMapper.writeValueAsString(details)));
        } catch (JsonProcessingException | RuntimeException ex) {
            log.error("Could not write audit entry for {}", eventType, ex);
        }
    }
}
