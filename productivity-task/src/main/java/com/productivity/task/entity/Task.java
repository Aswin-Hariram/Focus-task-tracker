package com.productivity.task.entity;

import com.productivity.category.entity.Category;
import com.productivity.core.common.DateTimeUtils;
import com.productivity.core.entity.AuditableEntity;
import com.productivity.task.enums.Priority;
import com.productivity.task.enums.TaskStatus;
import com.productivity.task.enums.TaskTimerStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "tasks", indexes = {
        @Index(name = "idx_task_planned_date", columnList = "planned_date"),
        @Index(name = "idx_task_status", columnList = "status"),
        @Index(name = "idx_task_deadline", columnList = "deadline")
})
public class Task extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank(message = "Enter a task name") @Size(max = 140, message = "Use 140 characters or fewer")
    @Column(nullable = false, length = 140) private String name;
    @Size(max = 3000, message = "Use 3,000 characters or fewer") @Column(length = 3000) private String description;
    @NotNull(message = "Choose a planned date") @Column(nullable = false) private LocalDate plannedDate;
    private LocalDate deadline;
    @NotNull(message = "Choose a status") @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private TaskStatus status = TaskStatus.YET_TO_START;
    @NotNull(message = "Choose a priority") @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Priority priority = Priority.MEDIUM;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "category_id", nullable = false) private Category category;
    private LocalDateTime completedAt;
    @Size(max = 2000, message = "Use 2,000 characters or fewer") @Column(length = 2000) private String completionNote;
    @Column(nullable = false) private int iteration = 1;
    @Column(nullable = false) private boolean archived;
    private Integer boardPosition;
    @Column(name = "total_time_spent_seconds", nullable = false) private long totalTimeSpentSeconds = 0L;
    @NotNull @Enumerated(EnumType.STRING) @Column(name = "timer_status", nullable = false, length = 20) private TaskTimerStatus timerStatus = TaskTimerStatus.STOPPED;
    @Column(name = "timer_started_at") private LocalDateTime timerStartedAt;
    @Column(name = "timer_session_started_at") private LocalDateTime timerSessionStartedAt;
    @Column(name = "timer_accumulated_seconds", nullable = false) private long timerAccumulatedSeconds = 0L;
    @Convert(converter = TimerDailySeconds.class)
    @Column(name = "timer_daily_seconds", columnDefinition = "text")
    private Map<LocalDate, Long> timerDailySeconds;
    @Transient private int attachmentCount;
    @Transient private int progressUpdateCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name == null ? null : name.trim(); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDate getPlannedDate() { return plannedDate; }
    public void setPlannedDate(LocalDate plannedDate) { this.plannedDate = plannedDate; }
    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getCompletionNote() { return completionNote; }
    public void setCompletionNote(String completionNote) { this.completionNote = completionNote; }
    public int getIteration() { return iteration; }
    public void setIteration(int iteration) { this.iteration = Math.max(1, iteration); }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public boolean isOverdue() { return DateTimeUtils.isOverdue(deadline, LocalDate.now()) && status != TaskStatus.COMPLETED; }
    public Integer getBoardPosition() { return boardPosition; }
    public void setBoardPosition(Integer boardPosition) { this.boardPosition = boardPosition; }
    public int getAttachmentCount() { return attachmentCount; }
    public void setAttachmentCount(int attachmentCount) { this.attachmentCount = attachmentCount; }
    public int getProgressUpdateCount() { return progressUpdateCount; }
    public void setProgressUpdateCount(int progressUpdateCount) { this.progressUpdateCount = progressUpdateCount; }

    public long getTotalTimeSpentSeconds() { return totalTimeSpentSeconds; }
    public void setTotalTimeSpentSeconds(long totalTimeSpentSeconds) { this.totalTimeSpentSeconds = Math.max(0, totalTimeSpentSeconds); }
    public TaskTimerStatus getTimerStatus() { return timerStatus != null ? timerStatus : TaskTimerStatus.STOPPED; }
    public void setTimerStatus(TaskTimerStatus timerStatus) { this.timerStatus = timerStatus != null ? timerStatus : TaskTimerStatus.STOPPED; }
    public LocalDateTime getTimerStartedAt() { return timerStartedAt; }
    public Long getTimerStartedAtMillis() {
        return timerStartedAt == null ? null : timerStartedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
    public void setTimerStartedAt(LocalDateTime timerStartedAt) { this.timerStartedAt = timerStartedAt; }
    public LocalDateTime getTimerSessionStartedAt() { return timerSessionStartedAt; }
    public void setTimerSessionStartedAt(LocalDateTime value) { timerSessionStartedAt = value; }
    public long getTimerAccumulatedSeconds() { return timerAccumulatedSeconds; }
    public void setTimerAccumulatedSeconds(long timerAccumulatedSeconds) { this.timerAccumulatedSeconds = Math.max(0, timerAccumulatedSeconds); }

    public Map<LocalDate, Long> getTimerDailySeconds() {
        if (timerDailySeconds != null) return new HashMap<>(timerDailySeconds);
        Map<LocalDate, Long> totals = new HashMap<>();
        LocalDateTime lastRecordedAt = timerStartedAt == null ? getUpdatedAt() : timerStartedAt;
        if (timerAccumulatedSeconds > 0 && lastRecordedAt != null) {
            TimerDailySeconds.addInterval(totals, lastRecordedAt.minusSeconds(timerAccumulatedSeconds), lastRecordedAt);
        }
        return totals;
    }

    public void setTimerDailySeconds(Map<LocalDate, Long> totals) {
        timerDailySeconds = totals == null ? null : new HashMap<>(totals);
    }

    public long getActiveSessionSeconds(LocalDateTime now) {
        long seconds = timerAccumulatedSeconds;
        if (timerStatus == TaskTimerStatus.RUNNING && timerStartedAt != null && now != null) {
            long diff = java.time.Duration.between(timerStartedAt, now).getSeconds();
            if (diff > 0) {
                seconds += diff;
            }
        }
        return seconds;
    }

    public long getEffectiveTotalSeconds(LocalDateTime now) {
        return totalTimeSpentSeconds + getActiveSessionSeconds(now);
    }

    public String getFormattedTotalTime() {
        return formatSeconds(totalTimeSpentSeconds);
    }

    public String getFormattedCompactTime() {
        return formatCompactSeconds(totalTimeSpentSeconds);
    }

    public static String formatTimerDigits(long seconds) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    public static String formatSeconds(long totalSecs) {
        if (totalSecs <= 0) return "0s";
        long hours = totalSecs / 3600;
        long minutes = (totalSecs % 3600) / 60;
        long seconds = totalSecs % 60;
        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || (hours == 0 && minutes == 0)) sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    public static String formatCompactSeconds(long totalSecs) {
        if (totalSecs <= 0) return "0m";
        long hours = totalSecs / 3600;
        long minutes = (totalSecs % 3600) / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m";
        }
        return totalSecs + "s";
    }
}
