CREATE TABLE notification_logs (
    id                BIGSERIAL PRIMARY KEY,
    event_type        VARCHAR(32)  NOT NULL,
    recipient         VARCHAR(180) NOT NULL,
    subject           VARCHAR(255) NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    error_message     VARCHAR(500),
    booking_reference VARCHAR(16),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_logs_recipient ON notification_logs (recipient, created_at DESC);
CREATE INDEX idx_notification_logs_created ON notification_logs (created_at DESC);
CREATE INDEX idx_notification_logs_reference ON notification_logs (booking_reference);
