package com.skyflow.flight.service;

import com.skyflow.common.config.RedisCacheConfig;
import com.skyflow.common.exception.BadRequestException;
import com.skyflow.common.exception.ConflictException;
import com.skyflow.common.exception.ResourceNotFoundException;
import com.skyflow.flight.domain.Airplane;
import com.skyflow.flight.domain.Airport;
import com.skyflow.flight.domain.Flight;
import com.skyflow.flight.dto.CreateFlightRequest;
import com.skyflow.flight.dto.FlightDto;
import com.skyflow.flight.dto.FlightSearchRequest;
import com.skyflow.flight.dto.PageDto;
import com.skyflow.flight.dto.UpdateFlightRequest;
import com.skyflow.flight.repository.AirplaneRepository;
import com.skyflow.flight.repository.AirportRepository;
import com.skyflow.flight.repository.FlightRepository;
import com.skyflow.flight.repository.FlightSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlightService {

    private static final Logger log = LoggerFactory.getLogger(FlightService.class);

    private final FlightRepository flightRepository;
    private final AirportRepository airportRepository;
    private final AirplaneRepository airplaneRepository;

    public FlightService(FlightRepository flightRepository, AirportRepository airportRepository,
                         AirplaneRepository airplaneRepository) {
        this.flightRepository = flightRepository;
        this.airportRepository = airportRepository;
        this.airplaneRepository = airplaneRepository;
    }

    /**
     * The hot path. Results are cached under a key derived from the whole filter set, so repeated
     * searches for the same route and date never touch Postgres.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = RedisCacheConfig.CACHE_FLIGHT_SEARCH, key = "#request.cacheKey()")
    public PageDto<FlightDto> search(FlightSearchRequest request) {
        Page<Flight> page = flightRepository.findAll(
                FlightSpecifications.matching(request), pageable(request));
        log.debug("Search miss for {} -> {} result(s)", request.cacheKey(), page.getTotalElements());
        return PageDto.from(page, FlightMapper::toDto);
    }

    private Pageable pageable(FlightSearchRequest request) {
        Sort sort = switch (request.sortByOrDefault()) {
            case "price" -> Sort.by(Sort.Direction.ASC, "price");
            case "-price" -> Sort.by(Sort.Direction.DESC, "price");
            case "duration" -> Sort.by(Sort.Direction.ASC, "arrivalTime");
            default -> Sort.by(Sort.Direction.ASC, "departureTime");
        };
        return PageRequest.of(request.pageOrDefault(), request.sizeOrDefault(), sort);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = RedisCacheConfig.CACHE_FLIGHT, key = "#id")
    public FlightDto get(Long id) {
        return FlightMapper.toDto(flightRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight", id)));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheConfig.CACHE_FLIGHT_SEARCH, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheConfig.CACHE_SEAT_INVENTORY, allEntries = true)
    })
    public FlightDto create(CreateFlightRequest request) {
        if (!request.arrivalTime().isAfter(request.departureTime())) {
            throw new BadRequestException("arrivalTime must be after departureTime");
        }
        if (request.departureAirportId().equals(request.arrivalAirportId())) {
            throw new BadRequestException("departureAirportId and arrivalAirportId must differ");
        }
        if (flightRepository.existsByFlightNumberIgnoreCase(request.flightNumber())) {
            throw new ConflictException("Flight " + request.flightNumber() + " already exists");
        }

        Airplane airplane = airplaneRepository.findById(request.airplaneId())
                .orElseThrow(() -> new ResourceNotFoundException("Airplane", request.airplaneId()));
        Airport departure = airportRepository.findById(request.departureAirportId())
                .orElseThrow(() -> new ResourceNotFoundException("Airport", request.departureAirportId()));
        Airport arrival = airportRepository.findById(request.arrivalAirportId())
                .orElseThrow(() -> new ResourceNotFoundException("Airport", request.arrivalAirportId()));

        Flight flight = new Flight();
        flight.setFlightNumber(request.flightNumber().trim().toUpperCase());
        flight.setAirplane(airplane);
        flight.setDepartureAirport(departure);
        flight.setArrivalAirport(arrival);
        flight.setDepartureTime(request.departureTime());
        flight.setArrivalTime(request.arrivalTime());
        flight.setPrice(request.price());
        flight.setBoardingGate(request.boardingGate());
        // Seat count comes from the aircraft, exactly as the Express service did.
        flight.setTotalSeats(airplane.getCapacity());
        flight.setAvailableSeats(airplane.getCapacity());

        Flight saved = flightRepository.save(flight);
        log.info("Created flight {} ({} -> {})", saved.getFlightNumber(), departure.getCode(),
                arrival.getCode());
        return FlightMapper.toDto(saved);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheConfig.CACHE_FLIGHT, key = "#id"),
            @CacheEvict(cacheNames = RedisCacheConfig.CACHE_FLIGHT_SEARCH, allEntries = true)
    })
    public FlightDto update(Long id, UpdateFlightRequest request) {
        Flight flight = flightRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight", id));

        if (request.departureTime() != null) {
            flight.setDepartureTime(request.departureTime());
        }
        if (request.arrivalTime() != null) {
            flight.setArrivalTime(request.arrivalTime());
        }
        if (!flight.getArrivalTime().isAfter(flight.getDepartureTime())) {
            throw new BadRequestException("arrivalTime must be after departureTime");
        }
        if (request.price() != null) {
            flight.setPrice(request.price());
        }
        if (request.boardingGate() != null) {
            flight.setBoardingGate(request.boardingGate());
        }
        if (request.status() != null) {
            flight.setStatus(request.status());
        }
        return FlightMapper.toDto(flightRepository.save(flight));
    }
}
