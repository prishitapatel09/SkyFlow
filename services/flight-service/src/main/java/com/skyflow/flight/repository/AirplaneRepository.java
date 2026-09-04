package com.skyflow.flight.repository;

import com.skyflow.flight.domain.Airplane;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AirplaneRepository extends JpaRepository<Airplane, Long> {
}
