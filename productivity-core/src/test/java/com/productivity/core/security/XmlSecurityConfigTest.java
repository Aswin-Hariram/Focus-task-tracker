package com.productivity.core.security;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XmlSecurityConfigTest {

    @Test
    @DisplayName("Loads and matches regex rules from security-config.xml")
    void verifyRegexRulesLoadedFromXml() {
        XmlSecurityConfig config = XmlSecurityConfig.getInstance();

        // category-color regex
        assertThat(config.matchesRegex("category-color", "#2563EB")).isTrue();
        assertThat(config.matchesRegex("category-color", "invalid-color")).isFalse();
        assertThat(config.matchesRegex("category-color", "#12345")).isFalse();

        // safe-redirect regex
        assertThat(config.matchesRegex("safe-redirect", "/dashboard")).isTrue();
        assertThat(config.matchesRegex("safe-redirect", "/tasks/10?page=1")).isTrue();
        assertThat(config.matchesRegex("safe-redirect", "//evil.com")).isFalse();
        assertThat(config.matchesRegex("safe-redirect", "/\\evil.com")).isFalse();

        // csv-formula-injection regex
        assertThat(config.matchesRegex("csv-formula-injection", "=cmd")).isTrue();
        assertThat(config.matchesRegex("csv-formula-injection", "+123")).isTrue();
        assertThat(config.matchesRegex("csv-formula-injection", "-123")).isTrue();
        assertThat(config.matchesRegex("csv-formula-injection", "@SUM")).isTrue();
        assertThat(config.matchesRegex("csv-formula-injection", "Normal Name")).isFalse();

        // backup-entry regex
        assertThat(config.matchesRegex("backup-entry", "focus-backup.json")).isTrue();
        assertThat(config.matchesRegex("backup-entry", "attachments/123.bin")).isTrue();
        assertThat(config.matchesRegex("backup-entry", "attachments/evil.sh")).isFalse();
        assertThat(config.matchesRegex("backup-entry", "../evil.sh")).isFalse();
    }

    @Test
    @DisplayName("Parses security filter headers and cache control from XML")
    void verifySecurityFilterConfigLoaded() {
        XmlSecurityConfig config = XmlSecurityConfig.getInstance();

        assertThat(config.isSecurityFilterEnabled()).isTrue();
        assertThat(config.getSecurityHeaders())
                .containsKey("Content-Security-Policy")
                .containsKey("X-Frame-Options")
                .containsKey("X-Content-Type-Options")
                .containsKey("Referrer-Policy");

        assertThat(config.getSecurityHeaders().get("X-Frame-Options")).isEqualTo("DENY");
        assertThat(config.getSecurityHeaders().get("X-Content-Type-Options")).isEqualTo("nosniff");

        assertThat(config.getCacheControlPaths()).containsKey("/settings/data/backup");
        assertThat(config.getCacheControlPaths().get("/settings/data/backup")).contains("no-store");

        assertThat(config.isCsrfEnabled()).isTrue();
        assertThat(config.getCsrfHeaderName()).isEqualTo("X-CSRF-TOKEN");
        assertThat(config.getCsrfParamName()).isEqualTo("_csrf");
    }

    @Test
    @DisplayName("Parses rate limiting quotas and matches specific endpoints")
    void verifyRateLimitingRulesLoaded() {
        XmlSecurityConfig config = XmlSecurityConfig.getInstance();

        assertThat(config.isRateLimitingEnabled()).isTrue();
        assertThat(config.getDefaultRateLimit()).isEqualTo(120);

        // Specific endpoint rules
        var backupRule = config.findMatchingRateLimitRule("/settings/data/backup", "GET");
        assertThat(backupRule.limit()).isEqualTo(5);
        assertThat(backupRule.windowSeconds()).isEqualTo(60);

        var restoreRule = config.findMatchingRateLimitRule("/settings/data/restore", "POST");
        assertThat(restoreRule.limit()).isEqualTo(3);

        var exportRule = config.findMatchingRateLimitRule("/analytics/export", "GET");
        assertThat(exportRule.limit()).isEqualTo(10);

        // Fallback to default
        var defaultRule = config.findMatchingRateLimitRule("/tasks", "GET");
        assertThat(defaultRule.limit()).isEqualTo(120);
    }

    @Test
    @DisplayName("Hardened parser rejects XXE injection attempts (CWE-611 / Anthropic Skill)")
    void verifyXxeInjectionIsRejected() {
        XmlSecurityConfig customConfig = new XmlSecurityConfig();
        String xxePayload = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE foo [
                  <!ELEMENT foo ANY >
                  <!ENTITY xxe SYSTEM "file:///etc/passwd" >]>
                <security-configuration>
                    <regex-rules>
                        <rule id="test" pattern="&xxe;" />
                    </regex-rules>
                </security-configuration>
                """;

        ByteArrayInputStream in = new ByteArrayInputStream(xxePayload.getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> customConfig.loadFromStream(in))
                .isInstanceOf(Exception.class);
    }
}
