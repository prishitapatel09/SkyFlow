package com.skyflow.flight.service;

import com.skyflow.common.config.RedisCacheConfig;
import com.skyflow.common.exception.ResourceNotFoundException;
import com.skyflow.flight.domain.Airplane;
import com.skyflow.flight.dto.AirplaneDto;
import com.skyflow.flight.dto.AirplaneRequest;
import com.skyflow.flight.repository.AirplaneRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AirplaneService {

    private final AirplaneRepository airplaneRepository;

    public AirplaneService(AirplaneRepository airplaneRepository) {
        this.airplaneRepository = airplaneRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = RedisCacheConfig.CACHE_AIRPLANES, key = "'all'")
    public List<AirplaneDto> findAll() {
        return airplaneRepository.findAll().stream().map(FlightMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AirplaneDto get(Long id) {
        return FlightMapper.toDto(airplaneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Airplane", id)));
    }

    @Transactional
    @CacheEvict(cacheNames = RedisCacheConfig.CACHE_AIRPLANES, allEntries = true)
    public AirplaneDto create(AirplaneRequest request) {
        Airplane airplane = new Airplane();
        airplane.setModelNumber(request.modelNumber().trim());
        airplane.setCapacity(request.capacity());
        return FlightMapper.toDto(airplaneRepository.save(airplane));
    }
}
