package com.skyflow.flight.repository;

import com.skyflow.flight.domain.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FlightRepository extends JpaRepository<Flight, Long>, JpaSpecificationExecutor<Flight> {

    boolean existsByFlightNumberIgnoreCase(String flightNumber);

    @Query("""
            select f from Flight f
            join fetch f.departureAirport da join fetch da.city
            join fetch f.arrivalAirport aa join fetch aa.city
            join fetch f.airplane
            where f.id = :id
            """)
    Optional<Flight> findByIdWithDetails(@Param("id") Long id);

    /**
     * Reserves seats in a single conditional UPDATE, so two concurrent bookings for the last seat
     * cannot both succeed regardless of which replica serves them.
     *
     * @return number of rows changed: 1 on success, 0 when not enough seats remain
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Flight f set f.availableSeats = f.availableSeats - :seats
            where f.id = :id and f.availableSeats >= :seats
            """)
    int reserveSeats(@Param("id") Long id, @Param("seats") int seats);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Flight f set f.availableSeats =
                case when f.availableSeats + :seats > f.totalSeats then f.totalSeats
                     else f.availableSeats + :seats end
            where f.id = :id
            """)
    int releaseSeats(@Param("id") Long id, @Param("seats") int seats);
}
