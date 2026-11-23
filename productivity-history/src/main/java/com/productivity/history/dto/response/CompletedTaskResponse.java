package com.productivity.history.dto.response;

import com.productivity.task.entity.Task;
import com.productivity.task.enums.Priority;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CompletedTaskResponse(
        Long id,
        String name,
        String description,
        Long categoryId,
        String categoryName,
        String categoryColor,
        Priority priority,
        LocalDate plannedDate,
        LocalDate deadline,
        LocalDateTime completedAt,
        String completionNote,
        int iteration
) {
    public static CompletedTaskResponse from(Task task) {
        if (task == null) {
            return null;
        }
        return new CompletedTaskResponse(
                task.getId(),
                task.getName(),
                task.getDescription(),
                task.getCategory() != null ? task.getCategory().getId() : null,
                task.getCategory() != null ? task.getCategory().getName() : null,
                task.getCategory() != null ? task.getCategory().getColor() : null,
                task.getPriority(),
                task.getPlannedDate(),
                task.getDeadline(),
                task.getCompletedAt(),
                task.getCompletionNote(),
                task.getIteration()
        );
    }
}
