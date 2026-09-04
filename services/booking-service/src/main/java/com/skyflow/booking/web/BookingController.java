package com.skyflow.booking.web;

import com.skyflow.booking.domain.BookingStatus;
import com.skyflow.booking.dto.BookingCreatedDto;
import com.skyflow.booking.dto.BookingDto;
import com.skyflow.booking.dto.CancelBookingRequest;
import com.skyflow.booking.dto.CreateBookingRequest;
import com.skyflow.booking.service.BookingService;
import com.skyflow.common.api.ApiResponse;
import com.skyflow.common.security.AuthenticatedUser;
import com.skyflow.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BookingCreatedDto> create(@Valid @RequestBody CreateBookingRequest request,
                                                 @CurrentUser AuthenticatedUser user) {
        return ApiResponse.success(bookingService.create(request, user), "Booking created");
    }

    @GetMapping("/my")
    public ApiResponse<List<BookingDto>> mine(@CurrentUser AuthenticatedUser user,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(bookingService.findMine(user, page, size),
                "Successfully fetched your bookings");
    }

    @GetMapping
    public ApiResponse<List<BookingDto>> all(@CurrentUser AuthenticatedUser user,
                                             @RequestParam(required = false) BookingStatus status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(bookingService.findAll(user, status, page, size),
                "Successfully fetched bookings");
    }

    @GetMapping("/{id}")
    public ApiResponse<BookingDto> get(@PathVariable Long id, @CurrentUser AuthenticatedUser user) {
        return ApiResponse.success(bookingService.get(id, user), "Successfully fetched the booking");
    }

    @GetMapping("/reference/{reference}")
    public ApiResponse<BookingDto> getByReference(@PathVariable String reference,
                                                  @CurrentUser AuthenticatedUser user) {
        return ApiResponse.success(bookingService.getByReference(reference, user),
                "Successfully fetched the booking");
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<BookingDto> cancel(@PathVariable Long id,
                                          @RequestBody(required = false) CancelBookingRequest request,
                                          @CurrentUser AuthenticatedUser user) {
        String reason = request == null ? null : request.reason();
        return ApiResponse.success(bookingService.cancel(id, reason, user), "Booking cancelled");
    }
}
