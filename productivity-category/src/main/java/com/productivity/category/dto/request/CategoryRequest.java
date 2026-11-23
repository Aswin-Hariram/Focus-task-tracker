package com.productivity.category.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "Enter a category name")
        @Size(max = 60, message = "Use 60 characters or fewer")
        String name,

        @NotBlank(message = "Choose a category color")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Use a six-digit hex color")
        String color
) {}
