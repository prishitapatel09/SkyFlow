package com.skyflow.notification.web;

import com.skyflow.common.api.ApiResponse;
import com.skyflow.notification.domain.NotificationLog;
import com.skyflow.notification.repository.NotificationLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only view of what has been sent; used by the admin dashboard. */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationLogRepository logs;

    public NotificationController(NotificationLogRepository logs) {
        this.logs = logs;
    }

    @GetMapping
    public ApiResponse<List<NotificationLog>> list(@RequestParam(required = false) String recipient,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "50") int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 200));
        List<NotificationLog> entries = recipient == null || recipient.isBlank()
                ? logs.findAllByOrderByCreatedAtDesc(pageRequest).getContent()
                : logs.findByRecipientOrderByCreatedAtDesc(recipient, pageRequest).getContent();
        return ApiResponse.success(entries, "Successfully fetched notification history");
    }
}
