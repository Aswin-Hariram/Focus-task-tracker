package com.productivity.attachment.dto.response;

import com.productivity.attachment.entity.Attachment;
import java.time.LocalDateTime;

public record AttachmentResponse(
        Long id,
        Long taskId,
        Long progressUpdateId,
        String originalName,
        String storedName,
        String contentType,
        long sizeBytes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AttachmentResponse from(Attachment attachment) {
        if (attachment == null) return null;
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getTaskId(),
                attachment.getProgressUpdateId(),
                attachment.getOriginalName(),
                attachment.getStoredName(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getCreatedAt(),
                attachment.getUpdatedAt()
        );
    }
}
