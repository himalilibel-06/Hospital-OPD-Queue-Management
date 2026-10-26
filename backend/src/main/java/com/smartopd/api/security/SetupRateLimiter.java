package com.smartopd.api.security;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SetupRateLimiter - Sliding window rate limiter for initial setup endpoint.
 * 
 * Enforces maximum 10 requests per minute per client IP address on POST /api/setup.
 */
@Component
public class SetupRateLimiter {

    public static final int MAX_REQUESTS_PER_MINUTE = 10;
    private static final long WINDOW_MILLIS = 60_000L;

    private final Map<String, Deque<Long>> requestHistory = new ConcurrentHashMap<>();

    public synchronized boolean isAllowed(String ip) {
        String key = (ip == null || ip.isBlank()) ? "unknown" : ip.trim();
        long now = System.currentTimeMillis();

        Deque<Long> timestamps = requestHistory.computeIfAbsent(key, k -> new ArrayDeque<>());

        // Evict entries older than 60 seconds
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() > WINDOW_MILLIS) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
            return false;
        }

        timestamps.addLast(now);
        return true;
    }

    public synchronized void reset() {
        requestHistory.clear();
    }
}
