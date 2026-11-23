package com.productivity.app;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

final class EmbeddedPostgresSupport {
    private static EmbeddedPostgres postgres;

    private EmbeddedPostgresSupport() {}

    static synchronized void startIfNeeded(String[] args) {
        if (postgres != null || shouldUseExternalDatabase(args)) return;

        Path storageRoot = storageRoot();
        System.setProperty("app.storage.root", storageRoot.toString());
        Path dataDirectory = postgresDataDirectory(storageRoot);
        int port = parsePort(environment("EMBEDDED_POSTGRES_PORT", "5433"));

        try {
            postgres = EmbeddedPostgres.builder()
                    .setPort(port)
                    .setDataDirectory(dataDirectory)
                    .setCleanDataDirectory(false)
                    .setRegisterShutdownHook(true)
                    .start();

            System.setProperty("spring.datasource.url", postgres.getJdbcUrl("postgres", "postgres"));
            System.setProperty("spring.datasource.username", "postgres");
            System.setProperty("spring.datasource.password", "");
            System.setProperty("spring.datasource.driver-class-name", "org.postgresql.Driver");
        } catch (IOException ex) {
            throw new IllegalStateException("Bundled PostgreSQL could not start on port " + port
                    + ". Set EMBEDDED_POSTGRES_PORT to another free port or set DB_URL to use an external database.", ex);
        }
    }

    private static boolean shouldUseExternalDatabase(String[] args) {
        if ("false".equalsIgnoreCase(environment("APP_EMBEDDED_POSTGRES_ENABLED", "true"))) return true;
        if (hasText(System.getenv("DB_URL")) || hasText(System.getProperty("spring.datasource.url"))) return true;

        String profiles = environment("SPRING_PROFILES_ACTIVE", "").toLowerCase(Locale.ROOT);
        if (Arrays.stream(profiles.split(",")).map(String::trim).anyMatch("prod"::equals)) return true;

        return Arrays.stream(args).anyMatch(arg -> arg.startsWith("--spring.datasource.url=")
                || arg.equals("--spring.profiles.active=prod")
                || arg.startsWith("--spring.profiles.active=prod,"));
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("EMBEDDED_POSTGRES_PORT must be a number between 1 and 65535");
        }
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return hasText(value) ? value : fallback;
    }

    private static Path storageRoot() {
        String configured = System.getenv("APP_STORAGE_ROOT");
        if (hasText(configured)) return Path.of(configured).toAbsolutePath().normalize();
        if (!Boolean.getBoolean("focus.packaged")) return Path.of("./storage").toAbsolutePath().normalize();

        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) return Path.of(userHome, "Library", "Application Support", "Focus", "storage");
        return Path.of(userHome, ".local", "share", "focus", "storage");
    }

    private static Path postgresDataDirectory(Path storageRoot) {
        String configured = environment("APP_POSTGRES_DATA_DIR", null);
        if (!hasText(configured)) configured = environment("EMBEDDED_POSTGRES_DATA_DIR", null);
        if (hasText(configured)) return Path.of(configured).toAbsolutePath().normalize();

        Path defaultDir;
        if (!Boolean.getBoolean("focus.packaged")) {
            defaultDir = Path.of("./postgres-data").toAbsolutePath().normalize();
        } else {
            String userHome = System.getProperty("user.home");
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("mac")) {
                defaultDir = Path.of(userHome, "Library", "Application Support", "Focus", "postgres-data");
            } else {
                defaultDir = Path.of(userHome, ".local", "share", "focus", "postgres-data");
            }
        }

        // Migrate legacy directory from inside storage if defaultDir does not yet exist
        Path legacyDir = storageRoot.resolve("postgres-data");
        if (!Files.exists(defaultDir) && Files.exists(legacyDir)) {
            try {
                Files.move(legacyDir, defaultDir);
            } catch (Exception ignored) {
                return legacyDir;
            }
        }
        return defaultDir;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
