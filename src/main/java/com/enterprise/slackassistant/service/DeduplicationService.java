package com.enterprise.slackassistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

/**
 * Distributed deduplication service using Redis.
 * Prevents duplicate processing of Slack events across multiple pods.
 * Falls back to in-memory deduplication if Redis is unavailable.
 */
@Service
public class DeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(DeduplicationService.class);
    private static final String DEDUP_PREFIX = "slack:dedup:";
    private static final Duration DEDUP_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final boolean redisEnabled;

    // Fallback in-memory deduplication set
    private final Set<String> fallbackSet = Collections.newSetFromMap(
            Collections.synchronizedMap(
                    new java.util.LinkedHashMap<String, Boolean>(64, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(java.util.Map.Entry<String, Boolean> eldest) {
                            return size() > 500;
                        }
                    }
            )
    );

    public DeduplicationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisEnabled = isRedisAvailable();

        if (redisEnabled) {
            log.info("DeduplicationService: using REDIS with TTL={}", DEDUP_TTL);
        } else {
            log.warn("DeduplicationService: Redis unavailable, using IN-MEMORY fallback (won't work across pods!)");
        }
    }

    /**
     * Attempts to mark an event as processed.
     *
     * @param eventId the unique event identifier (e.g., Slack message timestamp)
     * @return true if this is a NEW event (not seen before), false if DUPLICATE
     */
    public boolean tryMarkAsProcessed(String eventId) {
        if (redisEnabled) {
            return tryMarkInRedis(eventId);
        } else {
            return tryMarkInMemory(eventId);
        }
    }

    /**
     * Checks if an event has already been processed.
     */
    public boolean isAlreadyProcessed(String eventId) {
        if (redisEnabled) {
            try {
                return Boolean.TRUE.equals(redisTemplate.hasKey(DEDUP_PREFIX + eventId));
            } catch (Exception e) {
                log.warn("Dedup: Redis check failed for eventId='{}': {}", eventId, e.getMessage());
                return fallbackSet.contains(eventId);
            }
        } else {
            return fallbackSet.contains(eventId);
        }
    }

    // ─── Redis Implementation ──────────────────────────────────────────────────

    private boolean tryMarkInRedis(String eventId) {
        try {
            // SETNX-style: only set if not exists, with TTL
            Boolean wasSet = redisTemplate.opsForValue()
                    .setIfAbsent(DEDUP_PREFIX + eventId, "1", DEDUP_TTL);

            if (Boolean.TRUE.equals(wasSet)) {
                log.debug("Dedup: REDIS marked new event | eventId='{}'", eventId);
                return true; // New event
            } else {
                log.info("⚡ Dedup: REDIS duplicate detected | eventId='{}'", eventId);
                return false; // Duplicate
            }
        } catch (Exception e) {
            log.warn("Dedup: Redis setIfAbsent failed for eventId='{}': {}", eventId, e.getMessage());
            // Fallback to memory on Redis failure
            return tryMarkInMemory(eventId);
        }
    }

    // ─── In-Memory Fallback ────────────────────────────────────────────────────

    private boolean tryMarkInMemory(String eventId) {
        boolean isNew = fallbackSet.add(eventId);
        if (isNew) {
            log.debug("Dedup: MEMORY marked new event | eventId='{}'", eventId);
        } else {
            log.info("⚡ Dedup: MEMORY duplicate detected | eventId='{}'", eventId);
        }
        return isNew;
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
}

