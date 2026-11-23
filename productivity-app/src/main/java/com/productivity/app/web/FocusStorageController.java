package com.productivity.app.web;

import com.productivity.app.service.FocusStorage;
import com.productivity.attachment.entity.Attachment;
import com.productivity.attachment.entity.ReferenceLink;
import com.productivity.attachment.repository.ReferenceLinkRepository;
import com.productivity.attachment.service.AttachmentService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@ControllerAdvice(assignableTypes = DashboardController.class)
public class FocusStorageController {
    private final FocusStorage storage;

    private final AttachmentService attachments;
    private final ReferenceLinkRepository links;

    public FocusStorageController(FocusStorage storage, AttachmentService attachments, ReferenceLinkRepository links) {
        this.storage = storage;
        this.attachments = attachments;
        this.links = links;
    }

    @GetMapping("/tasks/{id}/storage-materials")
    @ResponseBody
    public ResponseEntity<Map<String, List<Long>>> materials(@PathVariable Long id) throws IOException {
        if (storage.location() == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                "files", attachments.allFilesForTask(id).stream().map(Attachment::getId).toList(),
                "links", links.findByTaskIdOrderByCreatedAtDesc(id).stream().map(ReferenceLink::getId).toList()));
    }

    @ModelAttribute("focusLocation")
    public String focusLocation(HttpServletRequest request) throws IOException {
        if (!request.getRequestURI().equals(request.getContextPath() + "/settings")) return "";
        Path location = storage.location();
        return location == null ? "" : location.toString();
    }

    @PostMapping("/settings/storage")
    public String select(@RequestParam String folder, HttpServletRequest request, RedirectAttributes redirect) {
        requireLocal(request);
        try {
            storage.select(Path.of(folder.strip()));
            redirect.addFlashAttribute("success", "Focus storage folder saved.");
        } catch (IOException | IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", "Could not save Focus storage. " + ex.getMessage());
        }
        return "redirect:/settings";
    }

    @PostMapping("/settings/storage/browse")
    @ResponseBody
    public Map<String, String> browse(HttpServletRequest request) throws IOException, InterruptedException {
        requireLocal(request);
        if (!System.getProperty("os.name").startsWith("Mac")) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
        }
        Process picker = new ProcessBuilder("/usr/bin/osascript", "-e",
                "POSIX path of (choose folder with prompt \"Select where Focus will store task files\")")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try (var output = picker.getInputStream()) {
            String path = new String(output.readAllBytes(), StandardCharsets.UTF_8).stripTrailing();
            return Map.of("folder", picker.waitFor() == 0 ? path : "");
        } finally {
            picker.destroy();
        }
    }

    private static void requireLocal(HttpServletRequest request) {
        String host = request.getServerName();
        if (!("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "[::1]".equals(host) || "::1".equals(host))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        try {
            if (InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()) return;
        } catch (IOException ignored) {
            // Treat unrecognized addresses as nonlocal.
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
