package com.productivity.app.security;

import com.productivity.core.security.XmlSecurityConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Hardens HTTP response security headers according to XML configuration
 * and Anthropic Cybersecurity Skill: performing-security-headers-audit
 * (NIST CSF PR.PS-01, PR.DS-10, DE.CM-01; MITRE ATT&CK T1190, T1059.007).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        XmlSecurityConfig config = XmlSecurityConfig.getInstance();
        if (config.isSecurityFilterEnabled()) {
            // Apply all HTTP security headers configured in security-config.xml
            Map<String, String> headers = config.getSecurityHeaders();
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                // HSTS only when running over HTTPS or forwarded by secure reverse-proxy
                if ("Strict-Transport-Security".equalsIgnoreCase(entry.getKey())) {
                    if (request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"))) {
                        response.setHeader(entry.getKey(), entry.getValue());
                    }
                } else {
                    response.setHeader(entry.getKey(), entry.getValue());
                }
            }

            // Apply Cache-Control rules configured in security-config.xml
            String uri = request.getRequestURI();
            for (Map.Entry<String, String> pathRule : config.getCacheControlPaths().entrySet()) {
                if (uri.contains(pathRule.getKey()) || uri.startsWith(pathRule.getKey())) {
                    response.setHeader("Cache-Control", pathRule.getValue());
                    response.setHeader("Pragma", "no-cache");
                    response.setDateHeader("Expires", 0);
                    break;
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
