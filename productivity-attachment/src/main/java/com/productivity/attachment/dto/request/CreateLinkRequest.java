package com.productivity.attachment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateLinkRequest(
        @Size(max = 120, message = "Link label must be 120 characters or fewer")
        String label,

        @NotBlank(message = "Enter a valid URL")
        @Size(max = 1000, message = "URL must be 1,000 characters or fewer")
        @Pattern(regexp = "^(https?://).*", message = "URL must start with http:// or https://")
        String url
) {}
