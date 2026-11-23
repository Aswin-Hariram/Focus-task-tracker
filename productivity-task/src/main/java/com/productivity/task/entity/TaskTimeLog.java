package com.productivity.task.entity;

import com.productivity.core.entity.AuditableEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "task_time_logs", indexes = {
        @Index(name = "idx_task_time_logs_task_id", columnList = "task_id")
})
public class TaskTimeLog extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @NotNull
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @NotNull
    @Column(name = "ended_at", nullable = false)
    private LocalDateTime endedAt;

    @Column(name = "duration_seconds", nullable = false)
    private long durationSeconds;

    @Convert(converter = TimerDailySeconds.class)
    @Column(name = "daily_seconds", columnDefinition = "text")
    private Map<LocalDate, Long> dailySeconds;

    @Size(max = 500)
    @Column(length = 500)
    private String note;

    protected TaskTimeLog() {}

    public TaskTimeLog(Task task, LocalDateTime startedAt, LocalDateTime endedAt, long durationSeconds, String note) {
        this.task = task;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.durationSeconds = Math.max(0, durationSeconds);
        this.note = note == null ? null : note.trim();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(long durationSeconds) { this.durationSeconds = Math.max(0, durationSeconds); }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note == null ? null : note.trim(); }

    public Map<LocalDate, Long> getDailySeconds() {
        return dailySeconds == null ? null : Map.copyOf(dailySeconds);
    }

    public void setDailySeconds(Map<LocalDate, Long> totals) {
        dailySeconds = Map.copyOf(totals);
    }

    public long secondsOn(LocalDate day) {
        if (dailySeconds != null) return dailySeconds.getOrDefault(day, 0L);
        return TimerDailySeconds.onDay(endedAt.minusSeconds(durationSeconds), endedAt, day);
    }

    public String getFormattedDuration() {
        return Task.formatSeconds(durationSeconds);
    }
}
