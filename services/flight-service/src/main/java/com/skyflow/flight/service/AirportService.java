package com.skyflow.flight.service;

import com.skyflow.common.config.RedisCacheConfig;
import com.skyflow.common.exception.ConflictException;
import com.skyflow.common.exception.ResourceNotFoundException;
import com.skyflow.flight.domain.Airport;
import com.skyflow.flight.domain.City;
import com.skyflow.flight.dto.AirportDto;
import com.skyflow.flight.dto.AirportRequest;
import com.skyflow.flight.repository.AirportRepository;
import com.skyflow.flight.repository.CityRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AirportService {

    private final AirportRepository airportRepository;
    private final CityRepository cityRepository;

    public AirportService(AirportRepository airportRepository, CityRepository cityRepository) {
        this.airportRepository = airportRepository;
        this.cityRepository = cityRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = RedisCacheConfig.CACHE_AIRPORTS, key = "'all'")
    public List<AirportDto> findAll() {
        return airportRepository.findAllWithCity().stream().map(FlightMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AirportDto get(Long id) {
        return FlightMapper.toDto(airportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Airport", id)));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = RedisCacheConfig.CACHE_AIRPORTS, key = "'code:' + #code.toLowerCase()")
    public AirportDto getByCode(String code) {
        return FlightMapper.toDto(airportRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ResourceNotFoundException("Airport with code " + code)));
    }

    @Transactional(readOnly = true)
    public List<AirportDto> findByCity(String cityName) {
        return airportRepository.findByCityName(cityName).stream().map(FlightMapper::toDto).toList();
    }

    @Transactional
    @CacheEvict(cacheNames = RedisCacheConfig.CACHE_AIRPORTS, allEntries = true)
    public AirportDto create(AirportRequest request) {
        if (airportRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("Airport " + request.code() + " already exists");
        }
        City city = cityRepository.findById(request.cityId())
                .orElseThrow(() -> new ResourceNotFoundException("City", request.cityId()));

        Airport airport = new Airport();
        airport.setName(request.name().trim());
        airport.setCode(request.code().trim().toUpperCase());
        airport.setAddress(request.address());
        airport.setCity(city);
        return FlightMapper.toDto(airportRepository.save(airport));
    }

    @Transactional
    @CacheEvict(cacheNames = RedisCacheConfig.CACHE_AIRPORTS, allEntries = true)
    public void delete(Long id) {
        if (!airportRepository.existsById(id)) {
            throw new ResourceNotFoundException("Airport", id);
        }
        airportRepository.deleteById(id);
    }
}
