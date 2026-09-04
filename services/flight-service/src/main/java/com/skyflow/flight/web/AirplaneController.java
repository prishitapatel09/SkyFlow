package com.skyflow.flight.web;

import com.skyflow.common.api.ApiResponse;
import com.skyflow.flight.dto.AirplaneDto;
import com.skyflow.flight.dto.AirplaneRequest;
import com.skyflow.flight.service.AirplaneService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/airplanes")
public class AirplaneController {

    private final AirplaneService airplaneService;

    public AirplaneController(AirplaneService airplaneService) {
        this.airplaneService = airplaneService;
    }

    @GetMapping
    public ApiResponse<List<AirplaneDto>> list() {
        return ApiResponse.success(airplaneService.findAll(), "Successfully fetched all airplanes");
    }

    @GetMapping("/{id}")
    public ApiResponse<AirplaneDto> get(@PathVariable Long id) {
        return ApiResponse.success(airplaneService.get(id), "Successfully fetched the airplane");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AirplaneDto> create(@Valid @RequestBody AirplaneRequest request) {
        return ApiResponse.success(airplaneService.create(request), "Successfully created an airplane");
    }
}
