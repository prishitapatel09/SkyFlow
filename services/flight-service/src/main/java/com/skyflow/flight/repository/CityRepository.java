package com.skyflow.flight.repository;

import com.skyflow.flight.domain.City;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CityRepository extends JpaRepository<City, Long> {

    List<City> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    boolean existsByNameIgnoreCase(String name);
}
