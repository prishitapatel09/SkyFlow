package com.skyflow.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * The single entry point browsers talk to. It routes, rate limits, and - most importantly - is the
 * one place a JWT is verified: downstream services read identity from the headers this gateway
 * sets, which is why it strips any client-supplied copy of them.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
