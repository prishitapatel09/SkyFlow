package com.skyflow.payment.web;

import com.skyflow.common.api.ApiResponse;
import com.skyflow.payment.dto.CancelPaymentRequest;
import com.skyflow.payment.dto.ConfirmPaymentRequest;
import com.skyflow.payment.dto.CreatePaymentIntentRequest;
import com.skyflow.payment.dto.PaymentConfigDto;
import com.skyflow.payment.dto.PaymentDetailsDto;
import com.skyflow.payment.dto.PaymentIntentDto;
import com.skyflow.payment.dto.RefundDto;
import com.skyflow.payment.dto.RefundRequest;
import com.skyflow.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** Static route first: otherwise {@code /methods} would bind to {@code /{paymentIntentId}}. */
    @GetMapping("/methods")
    public ApiResponse<PaymentConfigDto> config() {
        return ApiResponse.success(paymentService.config(), "Supported payment methods");
    }

    @PostMapping("/intent")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaymentIntentDto> createIntent(
            @Valid @RequestBody CreatePaymentIntentRequest request) {
        return ApiResponse.success(paymentService.createIntent(request), "Payment intent created");
    }

    @GetMapping("/{paymentIntentId}")
    public ApiResponse<PaymentDetailsDto> get(@PathVariable String paymentIntentId) {
        return ApiResponse.success(paymentService.get(paymentIntentId), "Payment details");
    }

    @PostMapping("/{paymentIntentId}/confirm")
    public ApiResponse<PaymentIntentDto> confirm(@PathVariable String paymentIntentId,
                                                 @Valid @RequestBody ConfirmPaymentRequest request) {
        return ApiResponse.success(paymentService.confirm(paymentIntentId, request),
                "Payment confirmed");
    }

    /**
     * Pulls the current state from Stripe. The webhook is the primary path; this exists for the
     * success page, which lands before the webhook has necessarily arrived.
     */
    @PostMapping("/{paymentIntentId}/sync")
    public ApiResponse<PaymentDetailsDto> sync(@PathVariable String paymentIntentId) {
        return ApiResponse.success(paymentService.syncFromStripe(paymentIntentId), "Payment synced");
    }

    @PostMapping("/{paymentIntentId}/refund")
    public ApiResponse<RefundDto> refund(@PathVariable String paymentIntentId,
                                         @Valid @RequestBody(required = false) RefundRequest request) {
        RefundRequest refundRequest = request == null ? new RefundRequest(null, null) : request;
        return ApiResponse.success(paymentService.refund(paymentIntentId, refundRequest),
                "Refund issued");
    }

    @PostMapping("/{paymentIntentId}/cancel")
    public ApiResponse<PaymentIntentDto> cancel(
            @PathVariable String paymentIntentId,
            @RequestBody(required = false) CancelPaymentRequest request) {
        String reason = request == null ? null : request.cancellationReason();
        return ApiResponse.success(paymentService.cancel(paymentIntentId, reason), "Payment cancelled");
    }
}
