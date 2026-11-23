package com.productivity.app.web;

import com.productivity.task.entity.Task;
import com.productivity.task.enums.Priority;
import com.productivity.task.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

public class TaskForm {
    private Long id;
    @NotBlank(message = "Enter a task name") @Size(max = 140) private String name;
    @Size(max = 3000) private String description;
    @NotNull(message = "Choose a planned date")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate plannedDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate deadline;
    @NotNull(message = "Choose a status") private TaskStatus status = TaskStatus.YET_TO_START;
    @NotNull(message = "Choose a priority") private Priority priority = Priority.MEDIUM;
    @NotNull(message = "Choose a category") private Long categoryId;
    @Size(max = 2000) private String completionNote;

    private MultipartFile attachment;
    private java.util.List<MultipartFile> attachments;
    private String linkLabel;
    private String linkUrl;

    public static TaskForm from(Task task) {
        TaskForm form = new TaskForm();
        form.id = task.getId(); form.name = task.getName(); form.description = task.getDescription();
        form.plannedDate = task.getPlannedDate(); form.deadline = task.getDeadline(); form.status = task.getStatus();
        form.priority = task.getPriority(); form.categoryId = task.getCategory().getId(); form.completionNote = task.getCompletionNote();
        return form;
    }

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
    public LocalDate getPlannedDate() { return plannedDate; } public void setPlannedDate(LocalDate plannedDate) { this.plannedDate = plannedDate; }
    public LocalDate getDeadline() { return deadline; } public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
    public TaskStatus getStatus() { return status; } public void setStatus(TaskStatus status) { this.status = status; }
    public Priority getPriority() { return priority; } public void setPriority(Priority priority) { this.priority = priority; }
    public Long getCategoryId() { return categoryId; } public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCompletionNote() { return completionNote; } public void setCompletionNote(String completionNote) { this.completionNote = completionNote; }

    public MultipartFile getAttachment() { return attachment; }
    public void setAttachment(MultipartFile attachment) { this.attachment = attachment; }
    public java.util.List<MultipartFile> getAttachments() { return attachments; }
    public void setAttachments(java.util.List<MultipartFile> attachments) { this.attachments = attachments; }
    public String getLinkLabel() { return linkLabel; }
    public void setLinkLabel(String linkLabel) { this.linkLabel = linkLabel; }
    public String getLinkUrl() { return linkUrl; }
    public void setLinkUrl(String linkUrl) { this.linkUrl = linkUrl; }
}
