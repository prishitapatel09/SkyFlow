package com.skyflow.flight.service;

import com.skyflow.common.config.RedisCacheConfig;
import com.skyflow.common.event.EventPublisher;
import com.skyflow.common.event.EventType;
import com.skyflow.common.exception.ConflictException;
import com.skyflow.common.exception.ResourceNotFoundException;
import com.skyflow.flight.domain.City;
import com.skyflow.flight.dto.CityDto;
import com.skyflow.flight.dto.CityRequest;
import com.skyflow.flight.repository.CityRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class CityService {

    private final CityRepository cityRepository;
    private final EventPublisher eventPublisher;

    public CityService(CityRepository cityRepository, EventPublisher eventPublisher) {
        this.cityRepository = cityRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = RedisCacheConfig.CACHE_CITIES,
            key = "#nameFilter == null ? 'all' : #nameFilter.toLowerCase()")
    public List<CityDto> findAll(String nameFilter) {
        List<City> cities = nameFilter == null || nameFilter.isBlank()
                ? cityRepository.findAll()
                : cityRepository.findByNameContainingIgnoreCaseOrderByNameAsc(nameFilter.trim());
        return cities.stream().map(FlightMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CityDto get(Long id) {
        return FlightMapper.toDto(cityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("City", id)));
    }

    @Transactional
    @CacheEvict(cacheNames = RedisCacheConfig.CACHE_CITIES, allEntries = true)
    public CityDto create(CityRequest request) {
        if (cityRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new ConflictException("City " + request.name() + " already exists");
        }
        City city = new City();
        city.setName(request.name().trim());
        if (request.countryCode() != null) {
            city.setCountryCode(request.countryCode().toUpperCase());
        }
        CityDto saved = FlightMapper.toDto(cityRepository.save(city));

        // Same event the Express controller published after creating a city.
        eventPublisher.publish(EventType.CITY_CREATED, null,
                Map.of("id", saved.id(), "name", saved.name()));
        return saved;
    }

    @Transactional
    @CacheEvict(cacheNames = RedisCacheConfig.CACHE_CITIES, allEntries = true)
    public CityDto update(Long id, CityRequest request) {
        City city = cityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("City", id));
        city.setName(request.name().trim());
        if (request.countryCode() != null) {
            city.setCountryCode(request.countryCode().toUpperCase());
        }
        return FlightMapper.toDto(cityRepository.save(city));
    }

    @Transactional
    @CacheEvict(cacheNames = RedisCacheConfig.CACHE_CITIES, allEntries = true)
    public void delete(Long id) {
        if (!cityRepository.existsById(id)) {
            throw new ResourceNotFoundException("City", id);
        }
        cityRepository.deleteById(id);
    }
}
