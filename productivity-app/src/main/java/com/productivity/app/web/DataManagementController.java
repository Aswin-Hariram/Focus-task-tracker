package com.productivity.app.web;

import com.productivity.app.service.BackupService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/settings/data")
public class DataManagementController {
    private final BackupService backups;

    public DataManagementController(BackupService backups) { this.backups = backups; }

    @GetMapping("/backup")
    public void backup(HttpServletResponse response) throws IOException {
        String filename = "focus-backup-" + LocalDate.now() + ".zip";
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString());
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        backups.createBackup(response.getOutputStream());
    }

    @PostMapping("/restore")
    public String restore(@RequestParam("backup") MultipartFile backup, RedirectAttributes redirect) {
        try {
            BackupService.RestoreResult result = backups.restore(backup);
            redirect.addFlashAttribute("success", "Backup restored: " + result.tasks() + " tasks, "
                    + result.progressUpdates() + " progress updates, and " + result.attachments() + " attachments");
        } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/settings";
    }

    @PostMapping("/reset")
    public String reset(@RequestParam(defaultValue = "false") boolean confirmed, RedirectAttributes redirect) {
        if (!confirmed) {
            redirect.addFlashAttribute("error", "Confirm the reset in Settings to continue.");
            return "redirect:/settings";
        }
        try {
            backups.resetWorkspace();
            redirect.addFlashAttribute("success", "App reset. Your workspace is ready for a fresh start.");
            redirect.addFlashAttribute("workspaceReset", true);
        } catch (UncheckedIOException ex) {
            redirect.addFlashAttribute("error", "Workspace data was cleared, but some uploaded files could not be removed.");
            redirect.addFlashAttribute("workspaceReset", true);
        } catch (IOException ex) {
            redirect.addFlashAttribute("error", "Could not access uploaded files. The app was not reset.");
        }
        return "redirect:/settings";
    }

}
