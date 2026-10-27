package com.sopaletras.security;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sopaletras.config.AppProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Límite de peticiones por IP (token bucket en memoria) para login, registro, recuperación
 * de contraseña, el generador público y las partidas.
 * <p>
 * Con varias instancias del backend conviene moverlo a Redis (por ejemplo con Bucket4j).
 * Detrás de un proxy, activa {@code server.forward-headers-strategy=native} para que la IP sea la real.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long MINUTE_MS = 60_000;
    private static final int MAX_TRACKED = 100_000;

    private final AppProperties.RateLimit cfg;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong();

    public RateLimitFilter(AppProperties props, ObjectMapper mapper, Clock clock) {
        this.cfg = props.rateLimit();
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!cfg.enabled()) {
            chain.doFilter(req, res);
            return;
        }
        String group = group(req);
        if (group == null) {
            chain.doFilter(req, res);
            return;
        }
        int perMinute = switch (group) {
            case "auth" -> cfg.authPerMinute();
            case "generate" -> cfg.generatePerMinute();
            default -> cfg.sessionsPerMinute();
        };
        long now = clock.millis();
        cleanup(now);
        Bucket bucket = buckets.computeIfAbsent(group + ":" + req.getRemoteAddr(), k -> new Bucket(perMinute, now));
        if (!bucket.tryConsume(perMinute, now)) {
            res.setHeader("Retry-After", "60");
            SecurityConfig.write(res, mapper, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Demasiados intentos. Espera un momento e inténtalo de nuevo.");
            return;
        }
        chain.doFilter(req, res);
    }

    private static String group(HttpServletRequest req) {
        String path = req.getRequestURI();
        if ("POST".equals(req.getMethod())) {
            if (path.startsWith("/api/v1/auth/") && !path.endsWith("/refresh") && !path.endsWith("/logout")) {
                return "auth";
            }
            if (path.equals("/api/v1/puzzles/generate")) {
                return "generate";
            }
            if (path.startsWith("/api/v1/sessions")) {
                return "sessions";
            }
        }
        return null;
    }

    private void cleanup(long now) {
        long last = lastCleanup.get();
        if ((now - last > MINUTE_MS || buckets.size() > MAX_TRACKED) && lastCleanup.compareAndSet(last, now)) {
            buckets.values().removeIf(b -> now - b.updatedAt > 2 * MINUTE_MS);
        }
    }

    private static final class Bucket {
        private double tokens;
        private long updatedAt;

        Bucket(int capacity, long now) {
            this.tokens = capacity;
            this.updatedAt = now;
        }

        synchronized boolean tryConsume(int perMinute, long now) {
            tokens = Math.min(perMinute, tokens + (now - updatedAt) * perMinute / (double) MINUTE_MS);
            updatedAt = now;
            if (tokens < 1) {
                return false;
            }
            tokens -= 1;
            return true;
        }
    }
}
