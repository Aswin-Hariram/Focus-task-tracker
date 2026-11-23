package com.productivity.core.security;

import com.productivity.core.exception.StorageException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityUtilsTest {

    @Test
    void sanitizeCsvCellPrefixesFormulaCharacters() {
        assertThat(SecurityUtils.sanitizeCsvCell("=cmd|' /C calc'!A0")).isEqualTo("'=cmd|' /C calc'!A0");
        assertThat(SecurityUtils.sanitizeCsvCell("@SUM(1+1)")).isEqualTo("'@SUM(1+1)");
        assertThat(SecurityUtils.sanitizeCsvCell("+12345")).isEqualTo("'+12345");
        assertThat(SecurityUtils.sanitizeCsvCell("-12345")).isEqualTo("'-12345");
        assertThat(SecurityUtils.sanitizeCsvCell("\ttext")).isEqualTo("'\ttext");
        assertThat(SecurityUtils.sanitizeCsvCell("\rtext")).isEqualTo("'\rtext");
        assertThat(SecurityUtils.sanitizeCsvCell("Normal Task Name")).isEqualTo("Normal Task Name");
        assertThat(SecurityUtils.sanitizeCsvCell(null)).isEqualTo("");
        assertThat(SecurityUtils.sanitizeCsvCell("")).isEqualTo("");
    }

    @Test
    void sanitizeRedirectUrlBlocksUnsafeRedirects() {
        String fallback = "/dashboard";
        // Protocol-relative URLs
        assertThat(SecurityUtils.sanitizeRedirectUrl("//evil.com", fallback)).isEqualTo(fallback);
        assertThat(SecurityUtils.sanitizeRedirectUrl("//attacker.com/path", fallback)).isEqualTo(fallback);

        // Backslash bypasses
        assertThat(SecurityUtils.sanitizeRedirectUrl("/\\evil.com", fallback)).isEqualTo(fallback);
        assertThat(SecurityUtils.sanitizeRedirectUrl("\\evil.com", fallback)).isEqualTo(fallback);

        // Absolute URLs
        assertThat(SecurityUtils.sanitizeRedirectUrl("https://evil.com", fallback)).isEqualTo(fallback);
        assertThat(SecurityUtils.sanitizeRedirectUrl("http://evil.com", fallback)).isEqualTo(fallback);
        assertThat(SecurityUtils.sanitizeRedirectUrl("javascript:alert(1)", fallback)).isEqualTo(fallback);

        // CRLF injection
        assertThat(SecurityUtils.sanitizeRedirectUrl("/tasks\r\nSet-Cookie: evil=1", fallback)).isEqualTo(fallback);

        // Legitimate relative paths
        assertThat(SecurityUtils.sanitizeRedirectUrl("/tasks", fallback)).isEqualTo("/tasks");
        assertThat(SecurityUtils.sanitizeRedirectUrl("/tasks/123", fallback)).isEqualTo("/tasks/123");
        assertThat(SecurityUtils.sanitizeRedirectUrl("/board?date=2026-11-22", fallback)).isEqualTo("/board?date=2026-11-22");
    }

    @Test
    void validatePathWithinRootPreventsDirectoryTraversal() {
        Path root = Path.of("storage/task-attachments");
        Path valid = root.resolve("normal-file.pdf");
        assertThat(SecurityUtils.validatePathWithinRoot(valid, root)).isEqualTo(valid.toAbsolutePath().normalize());

        Path invalid = root.resolve("../../etc/passwd");
        assertThatThrownBy(() -> SecurityUtils.validatePathWithinRoot(invalid, root))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Path traversal attempt detected");
    }

    @Test
    void sanitizeFilenameStripsDangerousCharacters() {
        assertThat(SecurityUtils.sanitizeFilename("../../etc/passwd")).isEqualTo("passwd");
        assertThat(SecurityUtils.sanitizeFilename("report\r\nSet-Cookie: evil.pdf")).isEqualTo("report__Set-Cookie_ evil.pdf");
        assertThat(SecurityUtils.sanitizeFilename("normal_report.pdf")).isEqualTo("normal_report.pdf");
        assertThat(SecurityUtils.sanitizeFilename(null)).isEqualTo("attachment");
    }

    @Test
    void validateExtensionMatchesContentTypeChecksIntegrity() {
        // Valid pairs
        SecurityUtils.validateExtensionMatchesContentType("doc.pdf", "application/pdf");
        SecurityUtils.validateExtensionMatchesContentType("photo.png", "image/png");
        SecurityUtils.validateExtensionMatchesContentType("photo.jpeg", "image/jpeg");
        SecurityUtils.validateExtensionMatchesContentType("photo.jpg", "image/jpeg");
        SecurityUtils.validateExtensionMatchesContentType("notes.txt", "text/plain");

        // Invalid extension disguised with permitted content type
        assertThatThrownBy(() -> SecurityUtils.validateExtensionMatchesContentType("malware.exe", "image/png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match declared type");

        assertThatThrownBy(() -> SecurityUtils.validateExtensionMatchesContentType("shell.php", "text/plain"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match declared type");
    }
}
