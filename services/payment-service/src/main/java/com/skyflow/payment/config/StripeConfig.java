package com.skyflow.payment.config;

import com.stripe.StripeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    private static final Logger log = LoggerFactory.getLogger(StripeConfig.class);

    /**
     * One client per process. Constructed even without a key so the service still starts in local
     * development - calls then fail with Stripe's own authentication error, which is clearer than a
     * failed context startup.
     */
    @Bean
    public StripeClient stripeClient(StripeProperties properties) {
        if (!properties.isConfigured()) {
            log.warn("skyflow.stripe.secret-key is not set; payment calls will fail until it is");
        }
        return new StripeClient(properties.getSecretKey());
    }
}
