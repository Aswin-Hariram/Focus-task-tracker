package com.productivity.task.dto.response;

import com.productivity.task.entity.Task;
import com.productivity.task.enums.Priority;
import com.productivity.task.enums.TaskStatus;
import com.productivity.task.enums.TaskTimerStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaskResponse(
        Long id,
        String name,
        String description,
        LocalDate plannedDate,
        LocalDate deadline,
        TaskStatus status,
        Priority priority,
        Long categoryId,
        String categoryName,
        String categoryColor,
        LocalDateTime completedAt,
        String completionNote,
        boolean archived,
        boolean overdue,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long totalTimeSpentSeconds,
        TaskTimerStatus timerStatus,
        LocalDateTime timerStartedAt,
        long timerAccumulatedSeconds,
        String formattedTotalTime,
        String formattedCompactTime
) {
    public static TaskResponse from(Task task) {
        if (task == null) return null;
        return new TaskResponse(
                task.getId(),
                task.getName(),
                task.getDescription(),
                task.getPlannedDate(),
                task.getDeadline(),
                task.getStatus(),
                task.getPriority(),
                task.getCategory() != null ? task.getCategory().getId() : null,
                task.getCategory() != null ? task.getCategory().getName() : null,
                task.getCategory() != null ? task.getCategory().getColor() : null,
                task.getCompletedAt(),
                task.getCompletionNote(),
                task.isArchived(),
                task.isOverdue(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getTotalTimeSpentSeconds(),
                task.getTimerStatus(),
                task.getTimerStartedAt(),
                task.getTimerAccumulatedSeconds(),
                task.getFormattedTotalTime(),
                task.getFormattedCompactTime()
        );
    }
}
