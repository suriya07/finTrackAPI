package com.example.financemanager.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A lightweight in-memory fixed-window rate limiter for the unauthenticated auth
 * endpoints, to blunt brute-force and email-enumeration/abuse. Keyed by client IP
 * plus request path. Deliberately dependency-free; for multi-instance deployments
 * this should be backed by a shared store (e.g. Redis/bucket4j).
 */
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/auth/login", "/auth/signup", "/auth/forgot-password");

    @Value("${app.rate-limit.max-requests:10}")
    private int maxRequests;

    @Value("${app.rate-limit.window-seconds:60}")
    private long windowSeconds;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!"POST".equalsIgnoreCase(request.getMethod()) || !LIMITED_PATHS.contains(request.getServletPath())) {
            chain.doFilter(request, response);
            return;
        }

        String key = clientIp(request) + ":" + request.getServletPath();
        long windowMillis = windowSeconds * 1000L;
        long now = System.currentTimeMillis();

        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startMillis >= windowMillis) {
                return new Window(now);
            }
            return existing;
        });

        if (window.count.incrementAndGet() > maxRequests) {
            long retryAfter = Math.max(1, (window.startMillis + windowMillis - now) / 1000);
            response.setStatus(429); // Too Many Requests
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"error\":\"Too many requests\",\"message\":\"Rate limit exceeded. Try again in "
                            + retryAfter + "s.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        final long startMillis;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long startMillis) {
            this.startMillis = startMillis;
        }
    }
}
