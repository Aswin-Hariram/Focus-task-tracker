package com.productivity.app.security;

import com.productivity.core.security.XmlSecurityConfig;
import com.productivity.core.security.XmlSecurityConfig.RateLimitRule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sliding Window In-Memory Rate Limiting Filter driven by security-config.xml,
 * implementing recommendations from Anthropic Cybersecurity Skill:
 * implementing-api-rate-limiting-and-throttling (NIST CSF PR.PS-01, PR.DS-10; MITRE ATT&CK T1190, T1110).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final boolean rateLimitEnabled;

    // Map: clientKey -> (endpointRuleKey -> timestamps of requests)
    private final Map<String, Map<String, Deque<Long>>> requestBuckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(@Value("${app.security.rate-limit.enabled:true}") boolean rateLimitEnabled) {
        this.rateLimitEnabled = rateLimitEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        XmlSecurityConfig config = XmlSecurityConfig.getInstance();
        if (!rateLimitEnabled || !config.isRateLimitingEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = resolveClientKey(request);
        String uri = request.getRequestURI();
        String method = request.getMethod();

        RateLimitRule rule = config.findMatchingRateLimitRule(uri, method);
        int maxRequests = rule.limit();
        int windowSeconds = rule.windowSeconds();
        long windowMillis = windowSeconds * 1000L;
        long now = System.currentTimeMillis();

        String ruleKey = rule.method() + ":" + rule.path();
        Map<String, Deque<Long>> clientRules = requestBuckets.computeIfAbsent(clientKey, k -> new ConcurrentHashMap<>());
        Deque<Long> timestamps = clientRules.computeIfAbsent(ruleKey, k -> new ArrayDeque<>());

        boolean allowed;
        int remaining;
        long oldestTimestamp = now;
        long resetSeconds;

        synchronized (timestamps) {
            // Evict timestamps outside the sliding window
            long cutoff = now - windowMillis;
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }

            if (timestamps.size() < maxRequests) {
                timestamps.addLast(now);
                allowed = true;
                remaining = maxRequests - timestamps.size();
            } else {
                allowed = false;
                remaining = 0;
            }

            if (!timestamps.isEmpty()) {
                oldestTimestamp = timestamps.peekFirst();
            }
            long windowExpiryMillis = oldestTimestamp + windowMillis;
            resetSeconds = Math.max(1, (windowExpiryMillis - now) / 1000);
        }

        // Standard Rate-Limit HTTP response headers
        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetSeconds));

        if (!allowed) {
            response.setStatus(429); // HTTP 429 Too Many Requests
            response.setHeader("Retry-After", String.valueOf(resetSeconds));

            String accept = request.getHeader("Accept");
            if (accept != null && accept.contains("application/json")) {
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(String.format(
                        "{\"error\":\"too_many_requests\",\"message\":\"Rate limit exceeded for %s. Try again in %d seconds.\",\"retry_after\":%d}",
                        rule.description(), resetSeconds, resetSeconds));
            } else {
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().write(String.format(
                        "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>429 Too Many Requests</title>" +
                        "<style>body{font-family:Inter,sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;background:#F8FAFC;color:#0F172A;}" +
                        ".card{background:#fff;padding:2.5rem;border-radius:12px;box-shadow:0 4px 6px -1px rgba(0,0,0,0.1);max-width:480px;text-align:center;}" +
                        "h1{color:#DC2626;font-size:1.5rem;margin-bottom:0.75rem;}p{color:#64748B;line-height:1.5;}" +
                        ".btn{display:inline-block;margin-top:1.25rem;padding:0.6rem 1.25rem;background:#2563EB;color:#fff;border-radius:6px;text-decoration:none;font-weight:500;}" +
                        "</style></head><body><div class='card'><h1>429 · Too Many Requests</h1>" +
                        "<p>Rate limit exceeded for <strong>%s</strong>. Please wait %d seconds before trying again.</p>" +
                        "<a class='btn' href='/dashboard'>Return to Dashboard</a></div></body></html>",
                        rule.description(), resetSeconds));
            }
            return;
        }

        // Periodic light cleanup of stale buckets if size grows
        if (requestBuckets.size() > 5000) {
            requestBuckets.entrySet().removeIf(entry -> entry.getValue().values().stream().allMatch(Deque::isEmpty));
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
