package com.productivity.progress.entity;

import com.productivity.core.entity.AuditableEntity;
import com.productivity.task.entity.Task;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

@Entity
@Table(name = "progress_updates")
public class ProgressUpdate extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @NotBlank(message = "Describe the progress made")
    @Size(max = 3000, message = "Use 3,000 characters or fewer")
    @Column(nullable = false, length = 3000)
    private String description;

    @Size(max = 1500, message = "Use 1,500 characters or fewer")
    @Column(length = 1500)
    private String reminder;

    @Column(nullable = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime recordedAt = LocalDateTime.now();

    @Column(nullable = false)
    private int iteration = 1;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReminder() { return reminder; }
    public void setReminder(String reminder) { this.reminder = reminder; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
    public int getIteration() { return iteration; }
    public void setIteration(int iteration) { this.iteration = Math.max(1, iteration); }
}
