package com.skyflow.flight.service;

import com.skyflow.common.config.RedisCacheConfig;
import com.skyflow.common.exception.ConflictException;
import com.skyflow.common.exception.ResourceNotFoundException;
import com.skyflow.flight.domain.Flight;
import com.skyflow.flight.dto.SeatAvailabilityDto;
import com.skyflow.flight.repository.FlightRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seat inventory: the other read path worth caching, and the one write path that has to be exactly
 * right. Reservation is a single conditional UPDATE, so overselling is impossible no matter how
 * many booking-service replicas race for the last seat.
 */
@Service
public class SeatInventoryService {

    private static final Logger log = LoggerFactory.getLogger(SeatInventoryService.class);

    private final FlightRepository flightRepository;

    public SeatInventoryService(FlightRepository flightRepository) {
        this.flightRepository = flightRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = RedisCacheConfig.CACHE_SEAT_INVENTORY, key = "#flightId")
    public SeatAvailabilityDto availability(Long flightId) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new ResourceNotFoundException("Flight", flightId));
        return new SeatAvailabilityDto(flight.getId(), flight.getTotalSeats(), flight.getAvailableSeats());
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheConfig.CACHE_SEAT_INVENTORY, key = "#flightId"),
            @CacheEvict(cacheNames = RedisCacheConfig.CACHE_FLIGHT, key = "#flightId"),
            @CacheEvict(cacheNames = RedisCacheConfig.CACHE_FLIGHT_SEARCH, allEntries = true)
    })
    public SeatAvailabilityDto reserve(Long flightId, int seats) {
        if (flightRepository.reserveSeats(flightId, seats) == 0) {
            // Either the flight is gone or it no longer has room; tell them apart for the caller.
            Flight flight = flightRepository.findById(flightId)
                    .orElseThrow(() -> new ResourceNotFoundException("Flight", flightId));
            throw new ConflictException("Only " + flight.getAvailableSeats()
                    + " seat(s) left on flight " + flight.getFlightNumber());
        }
        log.info("Reserved {} seat(s) on flight {}", seats, flightId);
        return availabilityAfterWrite(flightId);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheConfig.CACHE_SEAT_INVENTORY, key = "#flightId"),
            @CacheEvict(cacheNames = RedisCacheConfig.CACHE_FLIGHT, key = "#flightId"),
            @CacheEvict(cacheNames = RedisCacheConfig.CACHE_FLIGHT_SEARCH, allEntries = true)
    })
    public SeatAvailabilityDto release(Long flightId, int seats) {
        if (flightRepository.releaseSeats(flightId, seats) == 0) {
            throw new ResourceNotFoundException("Flight", flightId);
        }
        log.info("Released {} seat(s) on flight {}", seats, flightId);
        return availabilityAfterWrite(flightId);
    }

    /** Reads through the write, bypassing the cache annotations on {@link #availability(Long)}. */
    private SeatAvailabilityDto availabilityAfterWrite(Long flightId) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new ResourceNotFoundException("Flight", flightId));
        return new SeatAvailabilityDto(flight.getId(), flight.getTotalSeats(), flight.getAvailableSeats());
    }
}
