package com.skyflow.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis caching for flight search and inventory lookups - the hot read paths this platform is
 * built around. TTLs are per cache rather than global, matching the expiries the Express
 * {@code flight-service} set by hand.
 */
@Configuration
@EnableCaching
@ConditionalOnClass(RedisConnectionFactory.class)
public class RedisCacheConfig {

    /** Search results: rebuilt cheaply, so a longer TTL is worth the staleness. */
    public static final String CACHE_FLIGHT_SEARCH = "flightSearch";

    /** Individual flights, including the joined airports. */
    public static final String CACHE_FLIGHT = "flight";

    /** Seat availability - short TTL because bookings move it constantly. */
    public static final String CACHE_SEAT_INVENTORY = "seatInventory";

    public static final String CACHE_CITIES = "cities";
    public static final String CACHE_AIRPORTS = "airports";
    public static final String CACHE_AIRPLANES = "airplanes";

    @Bean
    public GenericJackson2JsonRedisSerializer redisValueSerializer() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        // Type information is required so cached records deserialize back to their own type
        // instead of a LinkedHashMap. Records are final, so NON_FINAL typing is not enough.
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                         GenericJackson2JsonRedisSerializer valueSerializer) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer));

        Map<String, RedisCacheConfiguration> perCache = Map.of(
                CACHE_FLIGHT_SEARCH, defaults.entryTtl(Duration.ofHours(1)),
                CACHE_FLIGHT, defaults.entryTtl(Duration.ofMinutes(30)),
                CACHE_SEAT_INVENTORY, defaults.entryTtl(Duration.ofSeconds(30)),
                CACHE_CITIES, defaults.entryTtl(Duration.ofHours(6)),
                CACHE_AIRPORTS, defaults.entryTtl(Duration.ofHours(6)),
                CACHE_AIRPLANES, defaults.entryTtl(Duration.ofHours(6)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(perCache)
                .transactionAware()
                .build();
    }
}
