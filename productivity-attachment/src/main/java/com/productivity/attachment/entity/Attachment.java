package com.productivity.attachment.entity;

import com.productivity.core.entity.AuditableEntity;
import jakarta.persistence.*;
import java.util.Set;

@Entity
@Table(name = "attachments")
public class Attachment extends AuditableEntity {
    private static final Set<String> INLINE_TYPES = Set.of(
            "application/pdf", "image/png", "image/jpeg", "image/gif", "image/webp", "image/avif",
            "image/bmp", "audio/mpeg", "audio/ogg", "audio/wav", "audio/mp4", "video/mp4", "video/webm", "video/ogg");
    private static final Set<String> PLAIN_TEXT_TYPES = Set.of(
            "text/plain", "text/csv", "text/markdown", "text/tab-separated-values", "application/json");

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long taskId;
    private Long progressUpdateId;
    @Column(nullable = false, length = 255) private String originalName;
    @Column(nullable = false, unique = true, length = 255) private String storedName;
    @Column(nullable = false, length = 120) private String contentType;
    @Column(nullable = false) private long sizeBytes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getProgressUpdateId() { return progressUpdateId; }
    public void setProgressUpdateId(Long progressUpdateId) { this.progressUpdateId = progressUpdateId; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getStoredName() { return storedName; }
    public void setStoredName(String storedName) { this.storedName = storedName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getPreviewContentType() {
        if (contentType == null) return null;
        if (PLAIN_TEXT_TYPES.contains(contentType)) return "text/plain";
        return INLINE_TYPES.contains(contentType) ? contentType : null;
    }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
}
