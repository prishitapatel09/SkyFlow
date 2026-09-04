package com.skyflow.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Append-only trail of every Stripe interaction; replaces the Mongo {@code audit_logs} collection. */
@Entity
@Table(name = "payment_audit_logs")
public class PaymentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "payment_intent_id")
    private String paymentIntentId;

    /** Raw event detail, kept as JSON text so the shape can change without a migration. */
    @Column(columnDefinition = "text")
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PaymentAuditLog of(String eventType, String paymentIntentId, String details) {
        PaymentAuditLog entry = new PaymentAuditLog();
        entry.eventType = eventType;
        entry.paymentIntentId = paymentIntentId;
        entry.details = details;
        return entry;
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
