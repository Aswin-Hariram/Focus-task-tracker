package com.productivity.task.dto.request;

import com.productivity.task.enums.Priority;
import com.productivity.task.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record TaskRequest(
        @NotBlank(message = "Enter a task name")
        @Size(max = 140, message = "Use 140 characters or fewer")
        String name,

        @Size(max = 3000, message = "Use 3,000 characters or fewer")
        String description,

        @NotNull(message = "Choose a planned date")
        LocalDate plannedDate,

        LocalDate deadline,

        @NotNull(message = "Choose a status")
        TaskStatus status,

        @NotNull(message = "Choose a priority")
        Priority priority,

        @NotNull(message = "Choose a category")
        Long categoryId,

        @Size(max = 2000, message = "Use 2,000 characters or fewer")
        String completionNote
) {}
