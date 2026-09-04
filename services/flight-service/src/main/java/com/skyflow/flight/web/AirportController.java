package com.skyflow.flight.web;

import com.skyflow.common.api.ApiResponse;
import com.skyflow.flight.dto.AirportDto;
import com.skyflow.flight.dto.AirportRequest;
import com.skyflow.flight.service.AirportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/airports")
public class AirportController {

    private final AirportService airportService;

    public AirportController(AirportService airportService) {
        this.airportService = airportService;
    }

    @GetMapping
    public ApiResponse<List<AirportDto>> list(@RequestParam(required = false) String city) {
        List<AirportDto> airports = city == null || city.isBlank()
                ? airportService.findAll()
                : airportService.findByCity(city);
        return ApiResponse.success(airports, "Successfully fetched all airports");
    }

    @GetMapping("/{id}")
    public ApiResponse<AirportDto> get(@PathVariable Long id) {
        return ApiResponse.success(airportService.get(id), "Successfully fetched the airport");
    }

    @GetMapping("/code/{code}")
    public ApiResponse<AirportDto> getByCode(@PathVariable String code) {
        return ApiResponse.success(airportService.getByCode(code), "Successfully fetched the airport");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AirportDto> create(@Valid @RequestBody AirportRequest request) {
        return ApiResponse.success(airportService.create(request), "Successfully created an airport");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        airportService.delete(id);
        return ApiResponse.success(null, "Successfully deleted the airport");
    }
}
