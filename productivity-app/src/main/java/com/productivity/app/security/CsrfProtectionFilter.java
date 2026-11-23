package com.productivity.app.security;

import com.productivity.core.security.XmlSecurityConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cross-Site Request Forgery (CSRF) defense implementing the Synchronizer Token Pattern
 * and Origin validation, following recommendations from Anthropic Cybersecurity Skill:
 * performing-csrf-attack-simulation (NIST CSF PR.PS-01; MITRE ATT&CK T1190).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class CsrfProtectionFilter extends OncePerRequestFilter {

    public static final String CSRF_ATTRIBUTE = "_csrf";
    public static final String CSRF_SESSION_KEY = "FOCUS_CSRF_TOKEN";
    public static final String CSRF_PARAM = "_csrf";
    public static final String CSRF_HEADER = "X-CSRF-TOKEN";
    public static final String XSRF_COOKIE_NAME = "XSRF-TOKEN";
    public static final String XSRF_HEADER = "X-XSRF-TOKEN";

    private final boolean csrfEnabled;
    private final SecureRandom secureRandom = new SecureRandom();

    public CsrfProtectionFilter(@Value("${app.security.csrf.enabled:true}") boolean csrfEnabled) {
        this.csrfEnabled = csrfEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        XmlSecurityConfig xmlConfig = XmlSecurityConfig.getInstance();
        boolean effectiveEnabled = csrfEnabled && xmlConfig.isCsrfEnabled();
        Set<String> safeMethods = xmlConfig.getCsrfSafeMethods();
        String paramName = xmlConfig.getCsrfParamName();
        String headerName = xmlConfig.getCsrfHeaderName();
        String cookieName = xmlConfig.getCsrfCookieName();

        HttpSession session = request.getSession(true);
        String token = (String) session.getAttribute(CSRF_SESSION_KEY);
        if (token == null) {
            byte[] bytes = new byte[32];
            secureRandom.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            session.setAttribute(CSRF_SESSION_KEY, token);
        }

        // Expose token to request attributes for Thymeleaf templates and controllers
        request.setAttribute(CSRF_ATTRIBUTE, token);

        // Expose token via standard XSRF cookie for frontend / AJAX scripts
        Cookie xsrfCookie = new Cookie(cookieName, token);
        xsrfCookie.setPath("/");
        xsrfCookie.setHttpOnly(false); // Accessible to JavaScript
        xsrfCookie.setSecure(request.isSecure());
        response.addCookie(xsrfCookie);

        // Safe methods do not modify server state
        if (!effectiveEnabled || safeMethods.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Defense-in-depth: Validate Origin header if present
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank() && !isAllowedOrigin(origin, request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Cross-origin request rejected");
            return;
        }

        // Validate state-changing requests
        String requestToken = request.getParameter(paramName);
        if (requestToken == null || requestToken.isBlank()) {
            requestToken = request.getHeader(headerName);
        }
        if (requestToken == null || requestToken.isBlank()) {
            requestToken = request.getHeader(XSRF_HEADER);
        }

        if (requestToken == null || !constantTimeEquals(token, requestToken.trim())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAllowedOrigin(String origin, HttpServletRequest request) {
        try {
            URI originUri = URI.create(origin);
            String originHost = originUri.getHost();
            if (originHost == null) return false;
            String serverName = request.getServerName();
            if (originHost.equalsIgnoreCase(serverName)) return true;
            if ("localhost".equalsIgnoreCase(originHost) || "127.0.0.1".equalsIgnoreCase(originHost)) {
                return "localhost".equalsIgnoreCase(serverName) || "127.0.0.1".equalsIgnoreCase(serverName);
            }
            return false;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                                     actual.getBytes(StandardCharsets.UTF_8));
    }
}
