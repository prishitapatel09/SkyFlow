-- Port of the Sequelize seeders, widened so the React UI and the natural language search have
-- routes to work with out of the box.

INSERT INTO cities (name, country_code) VALUES
    ('Boston', 'US'),
    ('New York', 'US'),
    ('San Francisco', 'US'),
    ('Chicago', 'US'),
    ('Seattle', 'US'),
    ('London', 'GB'),
    ('Paris', 'FR'),
    ('Tokyo', 'JP'),
    ('Bengaluru', 'IN'),
    ('Delhi', 'IN');

INSERT INTO airplanes (model_number, capacity) VALUES
    ('Boeing 737', 300),
    ('Boeing 777', 400),
    ('Airbus A330', 400),
    ('Airbus A320neo', 180);

INSERT INTO airports (name, code, address, city_id)
SELECT airport.name, airport.code, airport.address, cities.id
FROM (VALUES
    ('Logan International Airport',         'BOS', '1 Harborside Dr, Boston',        'Boston'),
    ('John F. Kennedy International',       'JFK', 'Queens, New York',               'New York'),
    ('San Francisco International Airport', 'SFO', 'San Mateo County, California',   'San Francisco'),
    ('O''Hare International Airport',       'ORD', '10000 W O''Hare Ave, Chicago',   'Chicago'),
    ('Seattle-Tacoma International',        'SEA', '17801 International Blvd',       'Seattle'),
    ('Heathrow Airport',                    'LHR', 'Longford, London',               'London'),
    ('Charles de Gaulle Airport',           'CDG', 'Roissy-en-France',               'Paris'),
    ('Haneda Airport',                      'HND', 'Ota City, Tokyo',                'Tokyo'),
    ('Kempegowda International Airport',    'BLR', 'Devanahalli, Bengaluru',         'Bengaluru'),
    ('Indira Gandhi International Airport', 'DEL', 'New Delhi',                      'Delhi')
) AS airport(name, code, address, city_name)
JOIN cities ON cities.name = airport.city_name;

-- A fortnight of departures on a handful of routes, priced per route.
INSERT INTO flights (flight_number, airplane_id, departure_airport_id, arrival_airport_id,
                     departure_time, arrival_time, price, boarding_gate, total_seats,
                     available_seats)
SELECT
    route.prefix || to_char(day.offset_days, 'FM000'),
    airplanes.id,
    departure.id,
    arrival.id,
    date_trunc('day', now()) + (day.offset_days || ' days')::interval + route.departure_offset,
    date_trunc('day', now()) + (day.offset_days || ' days')::interval + route.departure_offset
        + route.flight_duration,
    route.price,
    route.gate,
    airplanes.capacity,
    airplanes.capacity
FROM (VALUES
    ('SF', 'BOS', 'SFO', 'Boeing 777',     interval '7 hours',  interval '6 hours 30 minutes', 349.00, 'B12'),
    ('SB', 'SFO', 'BOS', 'Boeing 777',     interval '18 hours', interval '5 hours 30 minutes', 329.00, 'C4'),
    ('NY', 'BOS', 'JFK', 'Airbus A320neo', interval '9 hours',  interval '1 hour 20 minutes',   99.00, 'A2'),
    ('LN', 'JFK', 'LHR', 'Boeing 777',     interval '21 hours', interval '7 hours',            599.00, 'D8'),
    ('PA', 'BOS', 'CDG', 'Airbus A330',    interval '20 hours', interval '7 hours 15 minutes', 549.00, 'E1'),
    ('TK', 'SFO', 'HND', 'Boeing 777',     interval '13 hours', interval '11 hours',           899.00, 'G6'),
    ('BL', 'DEL', 'BLR', 'Boeing 737',     interval '6 hours',  interval '2 hours 45 minutes',  89.00, 'F3')
) AS route(prefix, from_code, to_code, airplane_model, departure_offset, flight_duration, price, gate)
JOIN airports departure ON departure.code = route.from_code
JOIN airports arrival ON arrival.code = route.to_code
JOIN airplanes ON airplanes.model_number = route.airplane_model
-- Column alias spelled out so the reference is unambiguous rather than relying on Postgres
-- resolving a bare function alias to its single column.
CROSS JOIN generate_series(1, 14) AS day(offset_days);
