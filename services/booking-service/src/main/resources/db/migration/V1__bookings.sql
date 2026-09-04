CREATE TABLE bookings (
    id                  BIGSERIAL PRIMARY KEY,
    booking_reference   VARCHAR(16)    NOT NULL UNIQUE,
    user_id             VARCHAR(64)    NOT NULL,
    contact_email       VARCHAR(180)   NOT NULL,
    contact_name        VARCHAR(120),
    flight_id           BIGINT         NOT NULL,
    flight_number       VARCHAR(16)    NOT NULL,
    origin_code         CHAR(3)        NOT NULL,
    destination_code    CHAR(3)        NOT NULL,
    departure_time      TIMESTAMPTZ    NOT NULL,
    arrival_time        TIMESTAMPTZ    NOT NULL,
    seats               INTEGER        NOT NULL CHECK (seats BETWEEN 1 AND 9),
    total_amount        NUMERIC(10, 2) NOT NULL CHECK (total_amount >= 0),
    currency            CHAR(3)        NOT NULL DEFAULT 'usd',
    status              VARCHAR(24)    NOT NULL DEFAULT 'PENDING_PAYMENT',
    payment_intent_id   VARCHAR(120),
    hold_expires_at     TIMESTAMPTZ,
    reminder_sent_at    TIMESTAMPTZ,
    cancellation_reason VARCHAR(255),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE passengers (
    id              BIGSERIAL PRIMARY KEY,
    booking_id      BIGINT       NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    full_name       VARCHAR(160) NOT NULL,
    seat_number     VARCHAR(8),
    passport_number VARCHAR(64)
);

CREATE INDEX idx_bookings_user ON bookings (user_id, created_at DESC);
CREATE INDEX idx_bookings_status ON bookings (status);

-- Drives the hold-expiry sweep: only unpaid holds are indexed, so the query the leader runs every
-- minute stays cheap no matter how many bookings exist.
CREATE INDEX idx_bookings_open_holds ON bookings (hold_expires_at)
    WHERE status = 'PENDING_PAYMENT';

-- Drives the reminder sweep.
CREATE INDEX idx_bookings_pending_reminders ON bookings (departure_time)
    WHERE status = 'CONFIRMED' AND reminder_sent_at IS NULL;

CREATE UNIQUE INDEX idx_bookings_payment_intent ON bookings (payment_intent_id)
    WHERE payment_intent_id IS NOT NULL;

CREATE INDEX idx_passengers_booking ON passengers (booking_id);
