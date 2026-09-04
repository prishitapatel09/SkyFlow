package com.skyflow.flight.repository;

import com.skyflow.flight.domain.Airport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AirportRepository extends JpaRepository<Airport, Long> {

    Optional<Airport> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("select a from Airport a join fetch a.city order by a.code")
    List<Airport> findAllWithCity();

    @Query("select a from Airport a join fetch a.city c where lower(c.name) = lower(:cityName)")
    List<Airport> findByCityName(String cityName);
}
