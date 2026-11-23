package com.productivity.app.security;

import com.productivity.category.entity.Category;
import com.productivity.category.repository.CategoryRepository;
import com.productivity.task.entity.Task;
import com.productivity.task.enums.Priority;
import com.productivity.task.enums.TaskStatus;
import com.productivity.task.repository.TaskRepository;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end security integration tests verifying defensive controls
 * implemented based on Anthropic Cybersecurity Skills:
 * - performing-security-headers-audit
 * - performing-csrf-attack-simulation
 * - testing-for-open-redirect-vulnerabilities
 * - performing-directory-traversal-testing
 * - exploiting-api-injection-vulnerabilities
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.storage.default-location=",
        "spring.datasource.url=jdbc:h2:mem:security-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.seed-demo-data=false",
        "app.storage.root=target/security-test-storage",
        "app.security.csrf.enabled=true"
})
class SecurityIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private CategoryRepository categories;
    @Autowired private TaskRepository tasks;

    private Category category;

    @BeforeEach
    void setUp() {
        tasks.deleteAll();
        categories.deleteAll();
        category = new Category();
        category.setName("Security Verification");
        category.setColor("#2563EB");
        category = categories.save(category);
    }

    @Nested
    @DisplayName("HTTP Security Headers (Anthropic Skill: performing-security-headers-audit)")
    class SecurityHeadersTests {

        @Test
        @DisplayName("Responses must include standard OWASP defensive headers")
        void verifyStandardSecurityHeaders() throws Exception {
            mvc.perform(get("/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                    .andExpect(header().string("Cross-Origin-Opener-Policy", "same-origin"))
                    .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"))
                    .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
                    .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("default-src 'self'")))
                    .andExpect(header().string("Permissions-Policy", org.hamcrest.Matchers.containsString("camera=(), microphone=()")));
        }

        @Test
        @DisplayName("Data export and backup endpoints must enforce no-cache directives")
        void verifyCacheControlOnSensitiveEndpoints() throws Exception {
            mvc.perform(get("/analytics/export"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-cache")))
                    .andExpect(header().string("Pragma", "no-cache"));

            mvc.perform(get("/settings/data/backup"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-cache")));
        }

        @Test
        @DisplayName("Secure HTTPS requests must include HSTS header")
        void verifyHstsOnSecureRequests() throws Exception {
            mvc.perform(get("/dashboard").secure(true))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Strict-Transport-Security", org.hamcrest.Matchers.containsString("max-age=31536000")));
        }
    }

    @Nested
    @DisplayName("CSRF Protection (Anthropic Skill: performing-csrf-attack-simulation)")
    class CsrfProtectionTests {

        @Test
        @DisplayName("POST request without CSRF token must be rejected with 403 Forbidden")
        void postWithoutCsrfTokenIsRejected() throws Exception {
            mvc.perform(post("/categories")
                            .param("name", "Malicious Category")
                            .param("color", "#EF4444"))
                    .andExpect(status().isForbidden());

            assertThat(categories.findByNameIgnoreCase("Malicious Category")).isEmpty();
        }

        @Test
        @DisplayName("POST request with invalid CSRF token must be rejected with 403 Forbidden")
        void postWithInvalidCsrfTokenIsRejected() throws Exception {
            mvc.perform(post("/categories")
                            .param("_csrf", "invalid-fake-token-value")
                            .param("name", "Forged Category")
                            .param("color", "#EF4444"))
                    .andExpect(status().isForbidden());

            assertThat(categories.findByNameIgnoreCase("Forged Category")).isEmpty();
        }

        @Test
        @DisplayName("POST request from unauthorized cross-origin must be rejected")
        void postFromUnauthorizedOriginIsRejected() throws Exception {
            // First get a valid CSRF token from session
            MvcResult getResult = mvc.perform(get("/dashboard")).andReturn();
            String token = (String) getResult.getRequest().getAttribute(CsrfProtectionFilter.CSRF_ATTRIBUTE);
            var session = getResult.getRequest().getSession();

            mvc.perform(post("/categories")
                            .session((org.springframework.mock.web.MockHttpSession) session)
                            .header("Origin", "https://evil-attacker-site.com")
                            .param("_csrf", token)
                            .param("name", "Cross Origin Category")
                            .param("color", "#EF4444"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST request with valid CSRF token in parameter succeeds")
        void postWithValidCsrfTokenParamSucceeds() throws Exception {
            MvcResult getResult = mvc.perform(get("/dashboard")).andReturn();
            String token = (String) getResult.getRequest().getAttribute(CsrfProtectionFilter.CSRF_ATTRIBUTE);
            var session = getResult.getRequest().getSession();

            mvc.perform(post("/categories")
                            .session((org.springframework.mock.web.MockHttpSession) session)
                            .param("_csrf", token)
                            .param("name", "Legitimate Category")
                            .param("color", "#10B981"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/categories"));

            assertThat(categories.findByNameIgnoreCase("Legitimate Category")).isPresent();
        }

        @Test
        @DisplayName("POST request with valid CSRF token in HTTP header succeeds")
        void postWithValidCsrfHeaderSucceeds() throws Exception {
            MvcResult getResult = mvc.perform(get("/dashboard")).andReturn();
            String token = (String) getResult.getRequest().getAttribute(CsrfProtectionFilter.CSRF_ATTRIBUTE);
            var session = getResult.getRequest().getSession();

            mvc.perform(post("/categories")
                            .session((org.springframework.mock.web.MockHttpSession) session)
                            .header(CsrfProtectionFilter.CSRF_HEADER, token)
                            .param("name", "Header Authorized Category")
                            .param("color", "#6366F1"))
                    .andExpect(status().is3xxRedirection());

            assertThat(categories.findByNameIgnoreCase("Header Authorized Category")).isPresent();
        }
    }

    @Nested
    @DisplayName("Open Redirect Prevention (Anthropic Skill: testing-for-open-redirect-vulnerabilities)")
    class OpenRedirectTests {

        @Test
        @DisplayName("Protocol-relative URLs (//evil.com) must be neutralized to safe fallback")
        void protocolRelativeRedirectNeutralized() throws Exception {
            Task task = createTestTask("Redirect Target");
            MvcResult getResult = mvc.perform(get("/dashboard")).andReturn();
            String token = (String) getResult.getRequest().getAttribute(CsrfProtectionFilter.CSRF_ATTRIBUTE);
            var session = getResult.getRequest().getSession();

            mvc.perform(post("/tasks/{id}/status", task.getId())
                            .session((org.springframework.mock.web.MockHttpSession) session)
                            .param("_csrf", token)
                            .param("status", "IN_PROGRESS")
                            .param("redirectUrl", "//evil.com/phishing"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/tasks/" + task.getId()));
        }

        @Test
        @DisplayName("Backslash bypasses (/\\evil.com) must be neutralized to safe fallback")
        void backslashRedirectNeutralized() throws Exception {
            Task task = createTestTask("Backslash Test");
            MvcResult getResult = mvc.perform(get("/dashboard")).andReturn();
            String token = (String) getResult.getRequest().getAttribute(CsrfProtectionFilter.CSRF_ATTRIBUTE);
            var session = getResult.getRequest().getSession();

            mvc.perform(post("/tasks/{id}/status", task.getId())
                            .session((org.springframework.mock.web.MockHttpSession) session)
                            .param("_csrf", token)
                            .param("status", "IN_PROGRESS")
                            .param("redirectUrl", "/\\evil.com"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/tasks/" + task.getId()));
        }

        @Test
        @DisplayName("External HTTP/HTTPS schemes must be neutralized to safe fallback")
        void externalSchemeRedirectNeutralized() throws Exception {
            Task task = createTestTask("External Scheme Test");
            MvcResult getResult = mvc.perform(get("/dashboard")).andReturn();
            String token = (String) getResult.getRequest().getAttribute(CsrfProtectionFilter.CSRF_ATTRIBUTE);
            var session = getResult.getRequest().getSession();

            mvc.perform(post("/tasks/{id}/status", task.getId())
                            .session((org.springframework.mock.web.MockHttpSession) session)
                            .param("_csrf", token)
                            .param("status", "IN_PROGRESS")
                            .param("redirectUrl", "https://attacker.com/steal"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/tasks/" + task.getId()));
        }

        @Test
        @DisplayName("Safe internal relative routes must be permitted")
        void safeInternalRedirectPermitted() throws Exception {
            Task task = createTestTask("Safe Route Test");
            MvcResult getResult = mvc.perform(get("/dashboard")).andReturn();
            String token = (String) getResult.getRequest().getAttribute(CsrfProtectionFilter.CSRF_ATTRIBUTE);
            var session = getResult.getRequest().getSession();

            mvc.perform(post("/tasks/{id}/status", task.getId())
                            .session((org.springframework.mock.web.MockHttpSession) session)
                            .param("_csrf", token)
                            .param("status", "IN_PROGRESS")
                            .param("redirectUrl", "/board?date=2026-11-22"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/board?date=2026-11-22"));
        }
    }

    @Nested
    @DisplayName("CSV Formula Injection Defense (Anthropic Skill: exploiting-api-injection-vulnerabilities)")
    class CsvFormulaInjectionTests {

        @Test
        @DisplayName("Tasks starting with formula operators (=, +, -, @) must be escaped with single quote in export")
        void formulaCharactersEscapedInCsvExport() throws Exception {
            Task task = new Task();
            task.setName("=cmd|' /C calc'!A0");
            task.setCategory(category);
            task.setPlannedDate(LocalDate.now());
            task.setStatus(TaskStatus.COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
            task.setCompletionNote("@SUM(1+1)");
            tasks.save(task);

            MvcResult result = mvc.perform(get("/analytics/export"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
                    .andReturn();

            String csvContent = result.getResponse().getContentAsString();
            // Verify leading dangerous characters are safely prepended with a single quote
            assertThat(csvContent).contains("\"'=cmd|' /C calc'!A0\"");
            assertThat(csvContent).contains("\"'@SUM(1+1)\"");
        }
    }

    @Nested
    @DisplayName("Zip Slip & Zip Bomb Protection (Anthropic Skill: performing-directory-traversal-testing)")
    class ZipSlipBombProtectionTests {

        @Test
        @DisplayName("Backup archive with path traversal (..) entry must be rejected")
        void archiveWithPathTraversalRejected() throws Exception {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                zos.putNextEntry(new ZipEntry("../../../evil.sh"));
                zos.write("echo pwned".getBytes());
                zos.closeEntry();
            }

            MockMultipartFile badZip = new MockMultipartFile("backup", "exploit.zip", "application/zip", baos.toByteArray());

            MvcResult getResult = mvc.perform(get("/dashboard")).andReturn();
            String token = (String) getResult.getRequest().getAttribute(CsrfProtectionFilter.CSRF_ATTRIBUTE);
            var session = getResult.getRequest().getSession();

            mvc.perform(multipart("/settings/data/restore")
                            .file(badZip)
                            .session((org.springframework.mock.web.MockHttpSession) session)
                            .param("_csrf", token))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("error", org.hamcrest.Matchers.containsString("Backup contains an invalid entry")));
        }

        @Test
        @DisplayName("Backup archive with unexpected entry path must be rejected")
        void archiveWithUnexpectedEntryRejected() throws Exception {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                zos.putNextEntry(new ZipEntry("malicious/script.py"));
                zos.write("import os".getBytes());
                zos.closeEntry();
            }

            MockMultipartFile badZip = new MockMultipartFile("backup", "exploit2.zip", "application/zip", baos.toByteArray());

            MvcResult getResult = mvc.perform(get("/dashboard")).andReturn();
            String token = (String) getResult.getRequest().getAttribute(CsrfProtectionFilter.CSRF_ATTRIBUTE);
            var session = getResult.getRequest().getSession();

            mvc.perform(multipart("/settings/data/restore")
                            .file(badZip)
                            .session((org.springframework.mock.web.MockHttpSession) session)
                            .param("_csrf", token))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("error", org.hamcrest.Matchers.containsString("Backup contains an unexpected entry")));
        }
    }

    @Nested
    @DisplayName("API & Web Rate Limiting (Anthropic Skill: implementing-api-rate-limiting-and-throttling)")
    class RateLimitingTests {

        @Test
        @DisplayName("Responses must include standard rate limit headers")
        void verifyRateLimitHeadersPresent() throws Exception {
            mvc.perform(get("/dashboard").with(request -> { request.setRemoteAddr("192.168.1.100"); return request; }))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }

        @Test
        @DisplayName("Exceeding endpoint rate limit must return 429 Too Many Requests and Retry-After")
        void exceedingLimitReturns429TooManyRequests() throws Exception {
            String clientIp = "10.0.0.99";

            // Backup endpoint limit is 5 in XML config
            for (int i = 0; i < 5; i++) {
                mvc.perform(get("/settings/data/backup").with(request -> { request.setRemoteAddr(clientIp); return request; }))
                        .andExpect(status().isOk());
            }

            // 6th request must be rejected with 429
            mvc.perform(get("/settings/data/backup").with(request -> { request.setRemoteAddr(clientIp); return request; }))
                    .andExpect(status().is(429))
                    .andExpect(header().string("X-RateLimit-Remaining", "0"))
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("429 · Too Many Requests")));
        }

        @Test
        @DisplayName("JSON client receives structured JSON error on 429")
        void jsonClientReceivesJsonErrorOn429() throws Exception {
            String clientIp = "10.0.0.98";

            // Backup endpoint limit is 5 in XML config
            for (int i = 0; i < 5; i++) {
                mvc.perform(get("/settings/data/backup").with(request -> { request.setRemoteAddr(clientIp); return request; }));
            }

            // Exceeded request with Accept: application/json
            mvc.perform(get("/settings/data/backup")
                            .header("Accept", "application/json")
                            .with(request -> { request.setRemoteAddr(clientIp); return request; }))
                    .andExpect(status().is(429))
                    .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/json")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("\"error\":\"too_many_requests\"")));
        }
    }

    private Task createTestTask(String name) {
        Task task = new Task();
        task.setName(name);
        task.setDescription("Test task description");
        task.setCategory(category);
        task.setPlannedDate(LocalDate.now());
        task.setDeadline(LocalDate.now().plusDays(2));
        task.setPriority(Priority.MEDIUM);
        task.setStatus(TaskStatus.YET_TO_START);
        return tasks.save(task);
    }
}
