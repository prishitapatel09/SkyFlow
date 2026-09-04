package com.skyflow.flight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.skyflow.flight", "com.skyflow.common"})
@ConfigurationPropertiesScan(basePackages = {"com.skyflow.flight", "com.skyflow.common"})
public class FlightServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlightServiceApplication.class, args);
    }
}
