CREATE TABLE payments (
    id                BIGSERIAL PRIMARY KEY,
    payment_intent_id VARCHAR(120)   NOT NULL UNIQUE,
    booking_id        VARCHAR(64),
    flight_id         VARCHAR(64),
    customer_id       VARCHAR(120),
    customer_email    VARCHAR(180),
    amount            NUMERIC(10, 2) NOT NULL CHECK (amount >= 0),
    currency          CHAR(3)        NOT NULL,
    status            VARCHAR(32)    NOT NULL,
    refunded_amount   NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (refunded_amount >= 0),
    failure_message   VARCHAR(500),
    description       VARCHAR(255),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_payments_refund CHECK (refunded_amount <= amount)
);

CREATE INDEX idx_payments_customer ON payments (customer_id, created_at DESC);
CREATE INDEX idx_payments_email ON payments (customer_email, created_at DESC);
CREATE UNIQUE INDEX idx_payments_booking ON payments (booking_id) WHERE booking_id IS NOT NULL;

CREATE TABLE payment_audit_logs (
    id                BIGSERIAL PRIMARY KEY,
    event_type        VARCHAR(64) NOT NULL,
    payment_intent_id VARCHAR(120),
    details           TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_audit_created ON payment_audit_logs (created_at DESC);
CREATE INDEX idx_payment_audit_intent ON payment_audit_logs (payment_intent_id);
