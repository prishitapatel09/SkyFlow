package com.skyflow.payment.web;

import com.skyflow.common.api.ApiResponse;
import com.skyflow.payment.dto.CustomerDto;
import com.skyflow.payment.dto.CustomerRequest;
import com.skyflow.payment.dto.PaymentDetailsDto;
import com.skyflow.payment.service.PaymentService;
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
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final PaymentService paymentService;

    public CustomerController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CustomerDto> create(@Valid @RequestBody CustomerRequest request) {
        return ApiResponse.success(paymentService.createCustomer(request), "Customer created");
    }

    @GetMapping("/{customerId}")
    public ApiResponse<CustomerDto> get(@PathVariable String customerId) {
        return ApiResponse.success(paymentService.getCustomer(customerId), "Customer details");
    }

    @GetMapping("/{customerId}/payments")
    public ApiResponse<List<PaymentDetailsDto>> history(@PathVariable String customerId,
                                                        @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(paymentService.customerHistory(customerId, limit),
                "Payment history");
    }
}
