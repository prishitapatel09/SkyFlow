package com.skyflow.flight.web;

import com.skyflow.common.api.ApiResponse;
import com.skyflow.flight.dto.CreateFlightRequest;
import com.skyflow.flight.dto.FlightDto;
import com.skyflow.flight.dto.FlightSearchRequest;
import com.skyflow.flight.dto.PageDto;
import com.skyflow.flight.dto.SeatAvailabilityDto;
import com.skyflow.flight.dto.SeatChangeRequest;
import com.skyflow.flight.dto.UpdateFlightRequest;
import com.skyflow.flight.service.FlightService;
import com.skyflow.flight.service.SeatInventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/flights")
public class FlightController {

    private final FlightService flightService;
    private final SeatInventoryService seatInventoryService;

    public FlightController(FlightService flightService, SeatInventoryService seatInventoryService) {
        this.flightService = flightService;
        this.seatInventoryService = seatInventoryService;
    }

    /** {@code GET /api/v1/flights} - also serves the dedicated search route below. */
    @GetMapping
    public ApiResponse<PageDto<FlightDto>> list(@ModelAttribute FlightSearchRequest request) {
        return ApiResponse.success(flightService.search(request), "Successfully fetched flights");
    }

    @GetMapping("/search")
    public ApiResponse<PageDto<FlightDto>> search(@ModelAttribute FlightSearchRequest request) {
        return ApiResponse.success(flightService.search(request), "Successfully fetched flights");
    }

    @GetMapping("/{id}")
    public ApiResponse<FlightDto> get(@PathVariable Long id) {
        return ApiResponse.success(flightService.get(id), "Successfully fetched the flight");
    }

    @GetMapping("/{id}/seats")
    public ApiResponse<SeatAvailabilityDto> seats(@PathVariable Long id) {
        return ApiResponse.success(seatInventoryService.availability(id), "Successfully fetched seats");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FlightDto> create(@Valid @RequestBody CreateFlightRequest request) {
        return ApiResponse.success(flightService.create(request), "Successfully created a flight");
    }

    @PatchMapping("/{id}")
    public ApiResponse<FlightDto> update(@PathVariable Long id,
                                         @Valid @RequestBody UpdateFlightRequest request) {
        return ApiResponse.success(flightService.update(id, request), "Successfully updated the flight");
    }

    /**
     * Seat reservation, called by booking-service rather than by browsers. Kept on the flight
     * service because inventory belongs to the flight that owns it.
     */
    @PostMapping("/{id}/seats/reserve")
    public ApiResponse<SeatAvailabilityDto> reserve(@PathVariable Long id,
                                                    @Valid @RequestBody SeatChangeRequest request) {
        return ApiResponse.success(seatInventoryService.reserve(id, request.seats()), "Seats reserved");
    }

    @PostMapping("/{id}/seats/release")
    public ApiResponse<SeatAvailabilityDto> release(@PathVariable Long id,
                                                    @Valid @RequestBody SeatChangeRequest request) {
        return ApiResponse.success(seatInventoryService.release(id, request.seats()), "Seats released");
    }
}
