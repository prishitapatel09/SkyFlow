package com.skyflow.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Map;

/**
 * Envelope for every message on {@link RabbitTopology#EXCHANGE}.
 *
 * <p>Mirrors the {@code {type, data, timestamp}} shape the Express services published, with an
 * added {@code recipientEmail} so notification-service does not have to call back into other
 * services to find out who to mail.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SkyflowEvent(
        EventType type,
        String recipientEmail,
        Map<String, Object> data,
        Instant timestamp) {

    public static SkyflowEvent of(EventType type, String recipientEmail, Map<String, Object> data) {
        return new SkyflowEvent(type, recipientEmail, data == null ? Map.of() : data, Instant.now());
    }

    /** Reads a string attribute out of {@link #data()}, or {@code null} when absent. */
    public String string(String key) {
        Object value = data == null ? null : data.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
