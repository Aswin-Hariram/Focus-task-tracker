package com.productivity.attachment.entity;

import com.productivity.core.entity.AuditableEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "reference_links")
public class ReferenceLink extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long taskId;
    private Long progressUpdateId;
    @NotBlank @Size(max = 120) @Column(nullable = false, length = 120) private String label;
    @NotBlank @Size(max = 1000) @Column(nullable = false, length = 1000) private String url;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getProgressUpdateId() { return progressUpdateId; }
    public void setProgressUpdateId(Long progressUpdateId) { this.progressUpdateId = progressUpdateId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}
