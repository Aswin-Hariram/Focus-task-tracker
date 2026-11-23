package com.productivity.overdue.service;

import com.productivity.core.common.DateTimeUtils;
import com.productivity.task.entity.Task;
import com.productivity.task.service.TaskService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OverdueService {
    private final TaskService tasks;
    public OverdueService(TaskService tasks) { this.tasks = tasks; }
    public List<OverdueTask> find(Long categoryId) {
        return tasks.overdue().stream()
                .filter(task -> categoryId == null || task.getCategory().getId().equals(categoryId))
                .map(task -> new OverdueTask(task, DateTimeUtils.daysOverdue(task.getDeadline(), LocalDate.now())))
                .toList();
    }
    public record OverdueTask(Task task, long daysOverdue) {}
}
