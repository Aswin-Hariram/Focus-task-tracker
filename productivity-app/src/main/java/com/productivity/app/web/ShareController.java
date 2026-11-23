package com.productivity.app.web;

import com.productivity.core.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ShareController {

    public record BinaryRelease(
        String key,
        String platform,
        String name,
        String filename,
        String relativePath,
        String contentType,
        String displaySize,
        long bytes,
        boolean available,
        String description
    ) {}

    private record BinaryDef(
        String key,
        String platform,
        String name,
        String filename,
        String relativePath,
        String contentType,
        String defaultSize,
        String description
    ) {}

    private static final Map<String, BinaryDef> DEFINITIONS = new LinkedHashMap<>();

    static {
        DEFINITIONS.put("mac-dmg", new BinaryDef(
            "mac-dmg", "macOS", "Focus Native Disk Image Installer", "Focus-1.0.0.dmg",
            "mac/Focus-1.0.0.dmg", "application/x-apple-diskimage", "134 MB",
            "Native macOS installer DMG with embedded runtime, application icon, and launcher."
        ));
        DEFINITIONS.put("mac-zip", new BinaryDef(
            "mac-zip", "macOS", "Standalone Focus.app Bundle Archive", "Focus-1.0.0-macOS.zip",
            "mac/Focus-1.0.0-macOS.zip", "application/zip", "128 MB",
            "Portable zip archive containing the pre-packaged Focus.app application bundle."
        ));
        DEFINITIONS.put("win-setup", new BinaryDef(
            "win-setup", "Windows", "Focus Automated Setup Installer", "Focus-1.0.0-Setup.exe",
            "win/Focus-1.0.0-Setup.exe", "application/vnd.microsoft.portable-executable", "88.5 MB",
            "Self-contained native 64-bit Windows GUI installer with clean install prompt, automatic background service startup, and shortcuts."
        ));
        DEFINITIONS.put("win-zip", new BinaryDef(
            "win-zip", "Windows", "Focus Windows Portable Distribution", "Focus-1.0.0-Windows.zip",
            "win/Focus-1.0.0-Windows.zip", "application/zip", "85 MB",
            "Complete portable Windows package containing Focus.exe, Focus-Console.exe, focus.jar, and CLI tools."
        ));
        DEFINITIONS.put("win-exe", new BinaryDef(
            "win-exe", "Windows", "Focus Silent GUI Launcher", "Focus.exe",
            "win/Focus.exe", "application/octet-stream", "2.5 KB",
            "Native 64-bit Windows GUI launcher that boots Focus silently in background and opens your browser."
        ));
        DEFINITIONS.put("win-console", new BinaryDef(
            "win-console", "Windows", "Focus Console Launcher", "Focus-Console.exe",
            "win/Focus-Console.exe", "application/octet-stream", "2.5 KB",
            "Native 64-bit Windows console executable showing live terminal output."
        ));
        DEFINITIONS.put("jar", new BinaryDef(
            "jar", "Universal / Linux", "Universal Executable Spring Boot JAR", "focus.jar",
            "focus.jar", "application/java-archive", "96 MB",
            "Standalone executable fat JAR with embedded native PostgreSQL. Zero external DB required."
        ));
        DEFINITIONS.put("linux-cli", new BinaryDef(
            "linux-cli", "Linux", "Linux CLI Controller", "focus",
            "linux/focus", "application/x-sh", "5.4 KB",
            "Linux command-line controller supporting start, stop, status, logs, and systemd management."
        ));
    }

    public Path getBinDirectory() {
        Path direct = Path.of("bin").toAbsolutePath().normalize();
        if (Files.isDirectory(direct)) {
            return direct;
        }
        Path parent = Path.of("../bin").toAbsolutePath().normalize();
        if (Files.isDirectory(parent)) {
            return parent;
        }
        return direct;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    public List<BinaryRelease> getReleaseList() {
        Path binDir = getBinDirectory();
        List<BinaryRelease> list = new ArrayList<>();
        for (BinaryDef def : DEFINITIONS.values()) {
            Path target = binDir.resolve(def.relativePath()).normalize();
            boolean exists = Files.exists(target) && Files.isRegularFile(target);
            String sizeStr = def.defaultSize();
            long sizeBytes = 0;
            if (exists) {
                try {
                    sizeBytes = Files.size(target);
                    sizeStr = formatFileSize(sizeBytes);
                } catch (Exception ignored) {}
            }
            list.add(new BinaryRelease(
                def.key(), def.platform(), def.name(), def.filename(), def.relativePath(),
                def.contentType(), sizeStr, sizeBytes, exists, def.description()
            ));
        }
        return list;
    }

    private String resolveLanIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface iface : Collections.list(interfaces)) {
                if (iface.isLoopback() || !iface.isUp()) continue;
                for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    @GetMapping("/share")
    public String share(HttpServletRequest request, Model model) {
        String baseHost = request.getHeader("Host");
        String scheme = request.getScheme();
        String contextPath = request.getContextPath();

        String currentUrl;
        if (baseHost != null && !baseHost.isBlank()) {
            currentUrl = scheme + "://" + baseHost + contextPath + "/download";
        } else {
            currentUrl = scheme + "://" + request.getServerName() + ":" + request.getServerPort() + contextPath + "/download";
        }

        String lanIp = resolveLanIp();
        int serverPort = request.getServerPort();
        String portSuffix = (serverPort == 80 && "http".equalsIgnoreCase(scheme)) || (serverPort == 443 && "https".equalsIgnoreCase(scheme))
                ? "" : ":" + serverPort;
        String lanUrl = scheme + "://" + lanIp + portSuffix + contextPath + "/download";

        model.addAttribute("pageTitle", "Share Focus App");
        model.addAttribute("shareUrl", currentUrl);
        model.addAttribute("lanUrl", lanUrl);
        model.addAttribute("lanIp", lanIp);
        model.addAttribute("releases", getReleaseList());
        return "share";
    }

    @GetMapping("/download")
    public String downloadPage(Model model) {
        model.addAttribute("pageTitle", "Download Focus — Fresh Product Setup");
        model.addAttribute("releases", getReleaseList());
        return "download";
    }

    @GetMapping("/download/file/{fileKey}")
    public ResponseEntity<Resource> downloadBinary(@PathVariable String fileKey) {
        BinaryDef def = DEFINITIONS.get(fileKey);
        if (def == null) {
            throw new ResourceNotFoundException("Unknown binary key: " + fileKey);
        }

        Path binDir = getBinDirectory();
        Path filePath = binDir.resolve(def.relativePath()).normalize();
        if (!filePath.startsWith(binDir)) {
            throw new SecurityException("Directory traversal detected");
        }

        File file = filePath.toFile();
        if (!file.exists() || !file.canRead()) {
            throw new ResourceNotFoundException("Binary file " + def.filename() + " is not currently available on the host.");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(def.contentType()))
                .contentLength(file.length())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(def.filename()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .body(new FileSystemResource(file));
    }
}
