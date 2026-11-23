package com.productivity.app.web;

import com.productivity.app.service.TaskCsvExport;
import com.productivity.app.service.TaskExcelExport;
import com.productivity.attachment.service.AttachmentService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class TaskExportController {
    private static final Logger LOG = LoggerFactory.getLogger(TaskExportController.class);
    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final TaskCsvExport exports;
    private final TaskExcelExport excel;
    private final AttachmentService attachments;

    public TaskExportController(TaskCsvExport exports, TaskExcelExport excel, AttachmentService attachments) {
        this.exports = exports;
        this.excel = excel;
        this.attachments = attachments;
    }

    @GetMapping(value = "/tasks/{id}/export", produces = "text/csv")
    public ResponseEntity<byte[]> task(@PathVariable Long id) {
        return download("task-" + id + ".csv", exports.task(id), CSV);
    }

    @GetMapping(value = "/settings/data/tasks.csv", produces = "text/csv")
    public ResponseEntity<byte[]> allTasks() {
        return download("focus-tasks.csv", exports.allTasks(), CSV);
    }

    @GetMapping(value = "/analytics/export", produces = "text/csv")
    public ResponseEntity<byte[]> report(@RequestParam(required = false) String query,
                                         @RequestParam(required = false) Long category,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] csv = exports.completed(query, category, from, to);
        try {
            attachments.saveExportFile("productivity-report-" + LocalDate.now() + ".csv", csv);
        } catch (IOException | IllegalArgumentException ex) {
            LOG.warn("Could not save a local copy of the CSV report", ex);
        }
        return download("productivity-report.csv", csv, CSV);
    }

    @GetMapping(value = "/tasks/{id}/export.xlsx", produces = XLSX)
    public ResponseEntity<byte[]> taskExcel(@PathVariable Long id) throws IOException {
        return download("task-" + id + ".xlsx", excel.task(id), MediaType.parseMediaType(XLSX));
    }

    @GetMapping(value = "/settings/data/tasks.xlsx", produces = XLSX)
    public ResponseEntity<byte[]> allTasksExcel() throws IOException {
        return download("focus-tasks.xlsx", excel.allTasks(), MediaType.parseMediaType(XLSX));
    }

    @GetMapping(value = "/analytics/export.xlsx", produces = XLSX)
    public ResponseEntity<byte[]> reportExcel(@RequestParam(required = false) String query,
                                              @RequestParam(required = false) Long category,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) throws IOException {
        return download("productivity-report.xlsx", excel.completed(query, category, from, to), MediaType.parseMediaType(XLSX));
    }

    private ResponseEntity<byte[]> download(String filename, byte[] content, MediaType type) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(content);
    }
}
