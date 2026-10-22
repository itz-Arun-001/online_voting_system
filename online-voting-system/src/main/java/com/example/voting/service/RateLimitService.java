package com.example.voting.service;

import com.example.voting.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Sliding-window limiter held in memory. Resets on restart; fine for a single instance. */
@Service
public class RateLimitService {
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public void check(String key, int max, Duration window) {
        long now = System.currentTimeMillis();
        long cutoff = now - window.toMillis();
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < cutoff) q.pollFirst();
            if (q.size() >= max) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many requests. Please wait a few minutes and try again.");
            }
            q.addLast(now);
        }
    }

    public void purge() {
        long cutoff = System.currentTimeMillis() - Duration.ofHours(1).toMillis();
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || e.getValue().peekLast() < cutoff;
            }
        });
    }
}
