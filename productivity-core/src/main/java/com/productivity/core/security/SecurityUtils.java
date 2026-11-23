package com.productivity.core.security;

import com.productivity.core.common.AppConstants;
import com.productivity.core.exception.StorageException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Security utilities implementing defensive controls aligned with
 * Anthropic Cybersecurity Skills:
 * - performing-security-headers-audit
 * - performing-directory-traversal-testing
 * - testing-for-open-redirect-vulnerabilities
 * - exploiting-api-injection-vulnerabilities (CSV / Formula Injection)
 */
public final class SecurityUtils {

    private static final Map<String, Set<String>> ALLOWED_EXTENSIONS = Map.of(
            "application/pdf", Set.of(".pdf"),
            "image/png", Set.of(".png"),
            "image/jpeg", Set.of(".jpg", ".jpeg"),
            "text/plain", Set.of(".txt"),
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of(".docx")
    );

    private SecurityUtils() {}

    /**
     * Prevents CSV / Spreadsheet Formula Injection (CWE-1236).
     * If a cell begins with '=', '+', '-', '@', tab, or carriage return (matching XML rule 'csv-formula-injection'),
     * prepends a single quote so spreadsheet programs treat it strictly as text.
     */
    public static String sanitizeCsvCell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (XmlSecurityConfig.getInstance().matchesRegex("csv-formula-injection", value)) {
            return "'" + value;
        }
        return value;
    }

    /**
     * Prevents Open Redirect vulnerabilities (MITRE T1190, T1566).
     * Uses XML regex rule 'safe-redirect' to validate relative target paths,
     * rejecting protocol-relative URLs ("//..."), backslash-prefixed paths ("/\\..."),
     * CRLF characters, and external schemes.
     */
    public static String sanitizeRedirectUrl(String url, String defaultUrl) {
        if (url == null || url.isBlank()) {
            return defaultUrl;
        }
        String trimmed = url.trim();

        // Must match XML-configured safe redirect regular expression
        if (!XmlSecurityConfig.getInstance().matchesRegex("safe-redirect", trimmed)) {
            return defaultUrl;
        }

        // Must not contain CRLF characters (header injection protection)
        if (trimmed.indexOf('\r') != -1 || trimmed.indexOf('\n') != -1) {
            return defaultUrl;
        }

        try {
            URI uri = URI.create(trimmed);
            if (uri.isAbsolute() || uri.getHost() != null || uri.getAuthority() != null) {
                return defaultUrl;
            }
            String path = uri.getPath();
            if (path == null || !path.startsWith("/")) {
                return defaultUrl;
            }
            return trimmed;
        } catch (IllegalArgumentException e) {
            return defaultUrl;
        }
    }

    /**
     * Verifies that a resolved path remains strictly within the intended root directory,
     * preventing Path Traversal (MITRE T1083, NIST CSF PR.DS-10).
     */
    public static Path validatePathWithinRoot(Path targetPath, Path rootDir) {
        Path normalizedTarget = targetPath.toAbsolutePath().normalize();
        Path normalizedRoot = rootDir.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedRoot)) {
            throw new StorageException("Path traversal attempt detected: " + targetPath);
        }
        return normalizedTarget;
    }

    /**
     * Sanitizes filenames for safe use in HTTP Content-Disposition headers and storage,
     * stripping directory traversal components, control characters, quotes, and newlines.
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "attachment";
        }
        // Extract base filename only
        String base = Path.of(filename).getFileName().toString();
        // Remove quotes, semicolons, colons, control characters, and newlines
        String cleaned = base.replaceAll("[\"\\\\/:;\r\n\\x00-\\x1f]", "_").trim();
        return cleaned.isEmpty() ? "attachment" : cleaned;
    }

    /**
     * Validates that the file extension matches the declared MIME type,
     * preventing disguised executable/script uploads.
     */
    public static void validateExtensionMatchesContentType(String originalFilename, String contentType) {
        if (contentType == null || !AppConstants.ALLOWED_ATTACHMENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("File type is not supported");
        }
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new IllegalArgumentException("File must have a valid extension");
        }
        String ext = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase();
        Set<String> validExtensions = ALLOWED_EXTENSIONS.get(contentType);
        if (validExtensions == null || !validExtensions.contains(ext)) {
            throw new IllegalArgumentException("File extension '" + ext + "' does not match declared type '" + contentType + "'");
        }
    }
}
