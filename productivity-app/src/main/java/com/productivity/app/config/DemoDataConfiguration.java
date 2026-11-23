package com.productivity.app.config;

import com.productivity.category.entity.Category;
import com.productivity.category.repository.CategoryRepository;
import com.productivity.task.entity.Task;
import com.productivity.task.enums.Priority;
import com.productivity.task.enums.TaskStatus;
import com.productivity.task.repository.TaskRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoDataConfiguration implements ApplicationRunner {
    private final CategoryRepository categories;
    private final TaskRepository tasks;
    private final JdbcTemplate jdbc;

    public DemoDataConfiguration(CategoryRepository categories, TaskRepository tasks, JdbcTemplate jdbc) {
        this.categories = categories;
        this.tasks = tasks;
        this.jdbc = jdbc;
    }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (categories.count() > 0 || Boolean.TRUE.equals(jdbc.queryForObject(
                "select demo_seed_disabled from workspace_settings where id = 1", Boolean.class))) return;
        Category work = category("Work", "#2563EB");
        Category personal = category("Personal", "#7C3AED");
        Category learning = category("Learning", "#0891B2");
        task("Prepare weekly plan", "Review priorities and assign realistic dates for the coming week.", work, LocalDate.now(), LocalDate.now().plusDays(1), Priority.HIGH, TaskStatus.IN_PROGRESS);
        task("Read architecture chapter", "Continue the current software architecture book and capture useful notes.", learning, LocalDate.now(), LocalDate.now().plusDays(3), Priority.MEDIUM, TaskStatus.YET_TO_START);
        task("Schedule health check", "Book the annual appointment.", personal, LocalDate.now(), LocalDate.now().plusDays(7), Priority.LOW, TaskStatus.YET_TO_START);
        Task completed = task("Review monthly expenses", "Reconcile recurring expenses and subscriptions.", personal, LocalDate.now(), LocalDate.now(), Priority.MEDIUM, TaskStatus.COMPLETED);
        completed.setCompletedAt(LocalDateTime.now().minusHours(2)); completed.setCompletionNote("Reviewed recurring charges and updated the monthly budget.");
    }

    private Category category(String name, String color) { Category value = new Category(); value.setName(name); value.setColor(color); return categories.save(value); }
    private Task task(String name, String description, Category category, LocalDate planned, LocalDate deadline, Priority priority, TaskStatus status) {
        Task task = new Task(); task.setName(name); task.setDescription(description); task.setCategory(category); task.setPlannedDate(planned);
        task.setDeadline(deadline); task.setPriority(priority); task.setStatus(status); return tasks.save(task);
    }
}
