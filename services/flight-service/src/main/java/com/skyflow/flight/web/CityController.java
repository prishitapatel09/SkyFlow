package com.skyflow.flight.web;

import com.skyflow.common.api.ApiResponse;
import com.skyflow.flight.dto.CityDto;
import com.skyflow.flight.dto.CityRequest;
import com.skyflow.flight.service.CityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cities")
public class CityController {

    private final CityService cityService;

    public CityController(CityService cityService) {
        this.cityService = cityService;
    }

    @GetMapping
    public ApiResponse<List<CityDto>> list(@RequestParam(required = false) String name) {
        return ApiResponse.success(cityService.findAll(name), "Successfully fetched all cities");
    }

    @GetMapping("/{id}")
    public ApiResponse<CityDto> get(@PathVariable Long id) {
        return ApiResponse.success(cityService.get(id), "Successfully fetched a city");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CityDto> create(@Valid @RequestBody CityRequest request) {
        return ApiResponse.success(cityService.create(request), "Successfully created a city");
    }

    @PatchMapping("/{id}")
    public ApiResponse<CityDto> update(@PathVariable Long id, @Valid @RequestBody CityRequest request) {
        return ApiResponse.success(cityService.update(id, request), "Successfully updated a city");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        cityService.delete(id);
        return ApiResponse.success(null, "Successfully deleted a city");
    }
}
