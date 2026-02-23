package com.enterprise.slackassistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Distributed cache service using Redis.
 * Falls back to in-memory cache if Redis is unavailable.
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);
    private static final String CACHE_PREFIX = "slack:cache:";

    private final StringRedisTemplate redisTemplate;
    private final long ttlMinutes;
    private final boolean redisEnabled;

    // Fallback in-memory cache when Redis is unavailable
    private final ConcurrentHashMap<String, CacheEntry> fallbackStore = new ConcurrentHashMap<>();

    public CacheService(
            StringRedisTemplate redisTemplate,
            @Value("${cache.ttl-minutes:10}") long ttlMinutes) {
        this.redisTemplate = redisTemplate;
        this.ttlMinutes = ttlMinutes;
        this.redisEnabled = isRedisAvailable();

        if (redisEnabled) {
            log.info("CacheService: using REDIS cache with TTL={}min", ttlMinutes);
        } else {
            log.warn("CacheService: Redis unavailable, using IN-MEMORY fallback cache");
        }
    }

    /**
     * Builds a cache key for a user's intent query.
     */
    public String buildKey(String userId, String intentKey) {
        return userId + ":" + intentKey;
    }

    /**
     * Gets a cached response.
     */
    public Optional<String> get(String key) {
        if (redisEnabled) {
            return getFromRedis(key);
        } else {
            return getFromMemory(key);
        }
    }

    /**
     * Stores a response in the cache.
     */
    public void put(String key, String response) {
        if (redisEnabled) {
            putToRedis(key, response);
        } else {
            putToMemory(key, response);
        }
    }

    /**
     * Evicts a specific key from the cache.
     */
    public void evict(String key) {
        if (redisEnabled) {
            try {
                redisTemplate.delete(CACHE_PREFIX + key);
                log.info("         Cache: EVICTED | key='{}'", key);
            } catch (Exception e) {
                log.warn("         Cache: Redis evict failed for key='{}': {}", key, e.getMessage());
            }
        } else {
            fallbackStore.remove(key);
        }
    }

    // ─── Redis Implementation ──────────────────────────────────────────────────

    private Optional<String> getFromRedis(String key) {
        try {
            String value = redisTemplate.opsForValue().get(CACHE_PREFIX + key);
            if (value != null) {
                log.info("         Cache: REDIS HIT | key='{}'", key);
                return Optional.of(value);
            }
            log.info("         Cache: REDIS MISS | key='{}'", key);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("         Cache: Redis get failed for key='{}': {}", key, e.getMessage());
            // Fallback to memory on Redis failure
            return getFromMemory(key);
        }
    }

    private void putToRedis(String key, String response) {
        try {
            redisTemplate.opsForValue().set(
                    CACHE_PREFIX + key,
                    response,
                    Duration.ofMinutes(ttlMinutes)
            );
            log.info("         Cache: REDIS STORED | key='{}' ttl={}min", key, ttlMinutes);
        } catch (Exception e) {
            log.warn("         Cache: Redis put failed for key='{}': {}", key, e.getMessage());
            // Fallback to memory on Redis failure
            putToMemory(key, response);
        }
    }

    // ─── In-Memory Fallback ────────────────────────────────────────────────────

    private Optional<String> getFromMemory(String key) {
        CacheEntry entry = fallbackStore.get(key);
        if (entry == null) {
            log.info("         Cache: MEMORY MISS | key='{}'", key);
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            log.info("         Cache: MEMORY EXPIRED | key='{}'", key);
            fallbackStore.remove(key);
            return Optional.empty();
        }
        log.info("         Cache: MEMORY HIT | key='{}' (expires {})", key, entry.expiresAt());
        return Optional.of(entry.response());
    }

    private void putToMemory(String key, String response) {
        Instant expiresAt = Instant.now().plusSeconds(ttlMinutes * 60);
        fallbackStore.put(key, new CacheEntry(response, expiresAt));
        log.info("         Cache: MEMORY STORED | key='{}' expires={}", key, expiresAt);
    }

    private boolean isRedisAvailable() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if Redis is currently being used.
     */
    public boolean isUsingRedis() {
        return redisEnabled;
    }

    private record CacheEntry(String response, Instant expiresAt) {}
}
