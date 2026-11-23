package com.productivity.progress.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record ProgressRequest(
        @NotBlank(message = "Describe the progress made")
        @Size(max = 3000, message = "Use 3,000 characters or fewer")
        String description,

        @Size(max = 1500, message = "Use 1,500 characters or fewer")
        String reminder,

        LocalDateTime recordedAt
) {
    public ProgressRequest(String description, String reminder) {
        this(description, reminder, null);
    }
}
