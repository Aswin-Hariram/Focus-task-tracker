package com.productivity.attachment.dto.response;

import com.productivity.attachment.entity.ReferenceLink;
import java.time.LocalDateTime;

public record ReferenceLinkResponse(
        Long id,
        Long taskId,
        String label,
        String url,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReferenceLinkResponse from(ReferenceLink link) {
        if (link == null) return null;
        return new ReferenceLinkResponse(
                link.getId(),
                link.getTaskId(),
                link.getLabel(),
                link.getUrl(),
                link.getCreatedAt(),
                link.getUpdatedAt()
        );
    }
}
