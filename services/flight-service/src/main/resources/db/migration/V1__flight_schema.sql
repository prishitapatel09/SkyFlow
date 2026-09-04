-- Port of the Sequelize migrations (create-city, create-airport, create-airplane, create-flights).
-- Column names move to snake_case and seat availability plus a status column are added, because
-- inventory is now served from this service rather than recomputed per request.

CREATE TABLE cities (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(120) NOT NULL UNIQUE,
    country_code CHAR(2)      NOT NULL DEFAULT 'US',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE airplanes (
    id           BIGSERIAL PRIMARY KEY,
    model_number VARCHAR(80) NOT NULL,
    capacity     INTEGER     NOT NULL DEFAULT 200 CHECK (capacity > 0),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE airports (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(160) NOT NULL,
    code       CHAR(3)      NOT NULL UNIQUE,
    address    VARCHAR(255),
    city_id    BIGINT       NOT NULL REFERENCES cities (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_airports_city ON airports (city_id);

CREATE TABLE flights (
    id                   BIGSERIAL PRIMARY KEY,
    flight_number        VARCHAR(16)    NOT NULL UNIQUE,
    airplane_id          BIGINT         NOT NULL REFERENCES airplanes (id),
    departure_airport_id BIGINT         NOT NULL REFERENCES airports (id),
    arrival_airport_id   BIGINT         NOT NULL REFERENCES airports (id),
    departure_time       TIMESTAMPTZ    NOT NULL,
    arrival_time         TIMESTAMPTZ    NOT NULL,
    price                NUMERIC(10, 2) NOT NULL CHECK (price > 0),
    boarding_gate        VARCHAR(16),
    total_seats          INTEGER        NOT NULL CHECK (total_seats > 0),
    available_seats      INTEGER        NOT NULL CHECK (available_seats >= 0),
    status               VARCHAR(16)    NOT NULL DEFAULT 'scheduled',
    version              BIGINT         NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_flight_times CHECK (arrival_time > departure_time),
    CONSTRAINT chk_flight_seats CHECK (available_seats <= total_seats),
    CONSTRAINT chk_flight_route CHECK (departure_airport_id <> arrival_airport_id)
);

-- The composite index the search endpoint filters on; the cache absorbs repeats, this keeps the
-- misses cheap.
CREATE INDEX idx_flights_route_departure
    ON flights (departure_airport_id, arrival_airport_id, departure_time);
CREATE INDEX idx_flights_departure_time ON flights (departure_time);
CREATE INDEX idx_flights_price ON flights (price);
