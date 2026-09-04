package com.skyflow.booking.web;

import com.skyflow.cluster.ClusterStatus;
import com.skyflow.cluster.ClusterStatusProvider;
import com.skyflow.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes which replica is currently the master, what its term is, and how the workers are
 * loaded - the view you want open while killing a pod to watch failover happen.
 */
@RestController
@RequestMapping("/api/v1/cluster")
public class ClusterController {

    private final ClusterStatusProvider statusProvider;

    public ClusterController(ClusterStatusProvider statusProvider) {
        this.statusProvider = statusProvider;
    }

    @GetMapping("/status")
    public ApiResponse<ClusterStatus> status() {
        return ApiResponse.success(statusProvider.status(), "OK");
    }
}
