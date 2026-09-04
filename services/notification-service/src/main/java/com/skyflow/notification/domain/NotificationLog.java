package com.skyflow.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Record of every email attempt. Doubles as the delivery audit trail and as the answer to "did the
 * traveller get their confirmation?" without digging through logs.
 */
@Entity
@Table(name = "notification_logs")
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "booking_reference", length = 16)
    private String bookingReference;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static NotificationLog sent(String eventType, String recipient, String subject,
                                       String bookingReference) {
        NotificationLog entry = new NotificationLog();
        entry.eventType = eventType;
        entry.recipient = recipient;
        entry.subject = subject;
        entry.bookingReference = bookingReference;
        entry.status = "SENT";
        return entry;
    }

    public static NotificationLog failed(String eventType, String recipient, String subject,
                                         String bookingReference, String errorMessage) {
        NotificationLog entry = sent(eventType, recipient, subject, bookingReference);
        entry.status = "FAILED";
        entry.errorMessage = errorMessage == null ? null
                : errorMessage.substring(0, Math.min(errorMessage.length(), 500));
        return entry;
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
