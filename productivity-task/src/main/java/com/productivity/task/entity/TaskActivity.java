package com.productivity.task.entity;

import com.productivity.task.enums.TaskStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "task_activity")
public class TaskActivity {
    public enum Action {
        CREATED("Created", "Task created", "plus"),
        COMPLETED("Completed", "Marked as completed", "check-circle"),
        STATUS_CHANGED("Status changed", "Status changed", "arrow"),
        UPDATED("Updated", "Task updated", "edit"),
        DELETED("Deleted", "Task deleted", "trash");

        private final String label;
        private final String title;
        private final String icon;

        Action(String label, String title, String icon) {
            this.label = label;
            this.title = title;
            this.icon = icon;
        }

        public String getLabel() { return label; }
        public String getTitle() { return title; }
        public String getIcon() { return icon; }
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long taskId;
    @Column(nullable = false, length = 140) private String taskName;
    @Column(nullable = false) private Long categoryId;
    @Column(nullable = false, length = 60) private String categoryName;
    @Column(nullable = false, length = 7) private String categoryColor;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Action action;
    @Enumerated(EnumType.STRING) @Column(length = 30) private TaskStatus status;
    @Enumerated(EnumType.STRING) @Column(length = 30) private TaskStatus previousStatus;
    @Column(nullable = false) private LocalDateTime occurredAt;
    @Column(length = 2000) private String note;
    private Long progressUpdateId;

    protected TaskActivity() {}

    public TaskActivity(Long taskId, String taskName, Long categoryId, String categoryName, String categoryColor,
                        Action action, TaskStatus status, TaskStatus previousStatus, LocalDateTime occurredAt, String note) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.categoryColor = categoryColor;
        this.action = action;
        this.status = status;
        this.previousStatus = previousStatus;
        this.occurredAt = occurredAt;
        this.note = note;
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public String getTaskName() { return taskName; }
    public Long getCategoryId() { return categoryId; }
    public String getCategoryName() { return categoryName; }
    public String getCategoryColor() { return categoryColor; }
    public Action getAction() { return action; }
    public TaskStatus getStatus() { return status; }
    public TaskStatus getPreviousStatus() { return previousStatus; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getNote() { return note; }
    public Long getProgressUpdateId() { return progressUpdateId; }
    public void setProgressUpdateId(Long progressUpdateId) { this.progressUpdateId = progressUpdateId; }

    public String getLabel() {
        return action == Action.STATUS_CHANGED && status != null ? status.getLabel() : action.getLabel();
    }

    public String getSummary() {
        return action == Action.STATUS_CHANGED && status != null ? "Moved to " + status.getLabel() : action.getTitle();
    }
}
