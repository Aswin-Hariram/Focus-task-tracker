package com.productivity.progress.dto.response;

import com.productivity.progress.entity.ProgressUpdate;
import java.time.LocalDateTime;

public record ProgressResponse(
        Long id,
        Long taskId,
        String description,
        String reminder,
        LocalDateTime recordedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ProgressResponse from(ProgressUpdate update) {
        if (update == null) return null;
        return new ProgressResponse(
                update.getId(),
                update.getTask() != null ? update.getTask().getId() : null,
                update.getDescription(),
                update.getReminder(),
                update.getRecordedAt(),
                update.getCreatedAt(),
                update.getUpdatedAt()
        );
    }
}
