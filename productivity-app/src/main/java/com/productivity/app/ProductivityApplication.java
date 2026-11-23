package com.productivity.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.awt.Desktop;
import java.net.URI;
import java.util.Locale;

@SpringBootApplication(scanBasePackages = "com.productivity")
@EntityScan(basePackages = "com.productivity")
@EnableJpaRepositories(basePackages = "com.productivity")
public class ProductivityApplication {

    private static final Logger log = LoggerFactory.getLogger(ProductivityApplication.class);

    public static void main(String[] args) {
        EmbeddedPostgresSupport.startIfNeeded(args);
        SpringApplication.run(ProductivityApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        boolean isPackaged = Boolean.getBoolean("focus.packaged");
        boolean openBrowserRequested = Boolean.getBoolean("focus.open-browser");
        if (!isPackaged && !openBrowserRequested) {
            return;
        }

        Environment env = event.getApplicationContext().getEnvironment();
        String port = env.getProperty("local.server.port", env.getProperty("server.port", "8484"));
        String url = "http://localhost:" + port;

        Thread launcherThread = new Thread(() -> {
            try {
                // Short grace period for network listener to stabilize
                Thread.sleep(600);
                if (isPackaged) {
                    ensureMacLaunchAgentRegistered();
                }
                openBrowser(url);
            } catch (Exception ex) {
                log.warn("Could not automatically launch browser: {}", ex.getMessage());
            }
        }, "focus-browser-launcher");
        launcherThread.setDaemon(true);
        launcherThread.start();
    }

    private static void ensureMacLaunchAgentRegistered() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("mac")) {
            return;
        }

        try {
            String userHome = System.getProperty("user.home");
            java.nio.file.Path agentsDir = java.nio.file.Path.of(userHome, "Library", "LaunchAgents");
            java.nio.file.Path plistFile = agentsDir.resolve("com.productivity.focus.plist");
            java.nio.file.Path logDir = java.nio.file.Path.of(userHome, "Library", "Logs", "Focus");

            // Look for installed app binary
            java.nio.file.Path appBinary = java.nio.file.Path.of("/Applications/Focus.app/Contents/MacOS/Focus");
            if (!java.nio.file.Files.exists(appBinary)) {
                String appPath = System.getProperty("jpackage.app-path");
                if (appPath != null && !appPath.isBlank()) {
                    java.nio.file.Path candidate = java.nio.file.Path.of(appPath);
                    if (java.nio.file.Files.exists(candidate)) {
                        appBinary = candidate;
                    }
                }
            }

            if (!java.nio.file.Files.exists(appBinary)) {
                return;
            }

            if (!java.nio.file.Files.exists(plistFile)) {
                java.nio.file.Files.createDirectories(agentsDir);
                java.nio.file.Files.createDirectories(logDir);

                String plistContent = String.format("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                    <plist version="1.0">
                    <dict>
                        <key>Label</key>
                        <string>com.productivity.focus</string>
                        <key>ProgramArguments</key>
                        <array>
                            <string>%s</string>
                        </array>
                        <key>RunAtLoad</key>
                        <true/>
                        <key>KeepAlive</key>
                        <dict>
                            <key>SuccessfulExit</key>
                            <false/>
                            <key>Crashed</key>
                            <true/>
                        </dict>
                        <key>StandardOutPath</key>
                        <string>%s/focus.launchd.log</string>
                        <key>StandardErrorPath</key>
                        <string>%s/focus.launchd.error.log</string>
                    </dict>
                    </plist>
                    """, appBinary.toAbsolutePath(), logDir.toAbsolutePath(), logDir.toAbsolutePath());

                java.nio.file.Files.writeString(plistFile, plistContent);
                log.info("Automatically registered macOS background LaunchAgent at {}", plistFile);

                try {
                    new ProcessBuilder("launchctl", "load", "-w", plistFile.toString()).start();
                } catch (Exception ignored) {}
            }
        } catch (Exception ex) {
            log.debug("Notice: LaunchAgent registration check: {}", ex.getMessage());
        }
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) {
            // Fall through to OS-specific command
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else if (os.contains("nix") || os.contains("nux")) {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception ex) {
            log.warn("Failed to open browser for {}: {}", url, ex.getMessage());
        }
    }
}
