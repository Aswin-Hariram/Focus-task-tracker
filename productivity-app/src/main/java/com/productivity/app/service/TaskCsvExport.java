package com.productivity.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productivity.attachment.entity.Attachment;
import com.productivity.attachment.entity.ReferenceLink;
import com.productivity.core.security.SecurityUtils;
import com.productivity.progress.entity.ProgressUpdate;
import com.productivity.task.entity.Task;
import com.productivity.task.entity.TaskActivity;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskCsvExport {
    private static final String HEADER = String.join(",",
            "Task", "Description", "Category", "Priority", "Status", "Iteration", "Planned date", "Deadline",
            "Created at", "Completed at", "Completion note", "Task ID", "Category ID", "Category color",
            "Category archived", "Last updated at", "Archived", "Board position", "Total time seconds",
            "Timer status", "Timer started at", "Timer session started at", "Timer accumulated seconds",
            "Timer daily seconds", "Progress updates", "Timeline events", "Iteration history", "Time logs",
            "Reference links", "Attachments");

    private final TaskExportData data;
    private final ObjectMapper json;

    TaskCsvExport(TaskExportData data, ObjectMapper json) {
        this.data = data;
        this.json = json;
    }

    public byte[] task(Long id) {
        return csv(data.task(id));
    }

    public byte[] allTasks() {
        return csv(data.allTasks());
    }

    public byte[] completed(String query, Long category, LocalDate from, LocalDate to) {
        return csv(data.completed(query, category, from, to));
    }

    private byte[] csv(List<Task> records) {
        StringBuilder output = new StringBuilder(HEADER).append("\r\n");
        for (Task task : records) {
            output.append(taskRow(task)).append("\r\n");
        }
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String taskRow(Task task) {
        Long id = task.getId();
        var category = task.getCategory();
        var details = data.details(id);
        var iterations = details.iterations().stream().map(BackupService.IterationData::from).toList();
        var timeLogs = details.timeLogs().stream().map(BackupService.TimeLogData::from).toList();
        return Stream.of(task.getName(), task.getDescription(), category.getName(), task.getPriority().getLabel(),
                        task.getStatus().getLabel(), task.getIteration(), task.getPlannedDate(), task.getDeadline(),
                        task.getCreatedAt(), task.getCompletedAt(), task.getCompletionNote(), id, category.getId(),
                        category.getColor(), category.isArchived(), task.getUpdatedAt(), task.isArchived(),
                        task.getBoardPosition(), task.getTotalTimeSpentSeconds(), task.getTimerStatus(),
                        task.getTimerStartedAt(), task.getTimerSessionStartedAt(), task.getTimerAccumulatedSeconds(),
                        json(task.getTimerDailySeconds()),
                        json(details.progress().stream().map(ProgressDetail::from).toList()),
                        json(details.events().stream().map(EventDetail::from).toList()),
                        json(iterations), json(timeLogs),
                        json(details.links().stream().map(LinkDetail::from).toList()),
                        json(details.attachments().stream().map(FileDetail::from).toList()))
                .map(value -> quote(Objects.toString(value, "")))
                .collect(Collectors.joining(","));
    }

    private String json(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not encode task details for CSV", ex);
        }
    }

    private static String quote(String value) {
        String first = value.substring(0, Math.min(1, value.length()));
        String stripped = value.stripLeading();
        String firstNonWhitespace = stripped.substring(0, Math.min(1, stripped.length()));
        boolean formula = !SecurityUtils.sanitizeCsvCell(first).equals(first)
                || !SecurityUtils.sanitizeCsvCell(firstNonWhitespace).equals(firstNonWhitespace);
        return "\"" + (formula ? "'" : "") + value.replace("\"", "\"\"") + "\"";
    }

    private record ProgressDetail(Long id, int iteration, LocalDateTime recordedAt, String description,
                                  String reminder, LocalDateTime createdAt, LocalDateTime updatedAt) {
        static ProgressDetail from(ProgressUpdate value) {
            return new ProgressDetail(value.getId(), value.getIteration(), value.getRecordedAt(), value.getDescription(),
                    value.getReminder(), value.getCreatedAt(), value.getUpdatedAt());
        }
    }

    private record EventDetail(Long id, LocalDateTime occurredAt, String action, String status, String previousStatus,
                               String taskName, Long categoryId, String categoryName, String categoryColor, String note, Long progressUpdateId) {
        static EventDetail from(TaskActivity value) {
            return new EventDetail(value.getId(), value.getOccurredAt(), value.getAction().name(),
                    Objects.toString(value.getStatus(), null), Objects.toString(value.getPreviousStatus(), null),
                    value.getTaskName(), value.getCategoryId(), value.getCategoryName(), value.getCategoryColor(),
                    value.getNote(), value.getProgressUpdateId());
        }
    }

    private record LinkDetail(Long id, Long progressUpdateId, String label, String url,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
        static LinkDetail from(ReferenceLink value) {
            return new LinkDetail(value.getId(), value.getProgressUpdateId(), value.getLabel(), value.getUrl(),
                    value.getCreatedAt(), value.getUpdatedAt());
        }
    }

    private record FileDetail(Long id, Long progressUpdateId, String filename, String contentType, long sizeBytes, String downloadPath,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
        static FileDetail from(Attachment value) {
            return new FileDetail(value.getId(), value.getProgressUpdateId(), value.getOriginalName(),
                    value.getContentType(), value.getSizeBytes(),
                    "/tasks/" + value.getTaskId() + "/attachments/" + value.getId(), value.getCreatedAt(), value.getUpdatedAt());
        }
    }
}
