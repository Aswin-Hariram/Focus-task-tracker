package com.productivity.history.controller;

import com.productivity.category.service.CategoryService;
import com.productivity.task.entity.TaskActivity;
import com.productivity.task.entity.TaskActivity.Action;
import com.productivity.task.repository.TaskActivityRepository;
import com.productivity.task.service.TaskService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HistoryController {
    private final TaskActivityRepository activity;
    private final CategoryService categories;
    private final TaskService tasks;

    public HistoryController(TaskActivityRepository activity, CategoryService categories, TaskService tasks) {
        this.activity = activity;
        this.categories = categories;
        this.tasks = tasks;
    }

    @GetMapping("/history")
    public String history(@RequestParam(required = false) String query,
                          @RequestParam(required = false) Long category,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(required = false) Action action,
                          @RequestParam(required = false) Long event,
                          @RequestParam(defaultValue = "activity") String tab,
                          @RequestParam(defaultValue = "list") String view,
                          Model model) {
        LocalDate today = LocalDate.now();
        boolean invalidRange = from != null && to != null && to.isBefore(from);
        List<TaskActivity> matching = invalidRange ? List.of() : activity.search(
                query == null ? "" : query.strip(), category,
                from == null ? LocalDate.of(1, 1, 1).atStartOfDay() : from.atStartOfDay(),
                to == null ? LocalDate.of(9999, 12, 31).atStartOfDay() : to.plusDays(1).atStartOfDay());
        List<TaskActivity> visible = matching.stream()
                .filter(item -> action == null || item.getAction() == action)
                .toList();
        var groups = visible.stream().collect(Collectors.groupingBy(item -> item.getOccurredAt().toLocalDate(),
                LinkedHashMap::new, Collectors.toList()));
        TaskActivity selected = visible.stream().filter(item -> Objects.equals(item.getId(), event))
                .findFirst().orElse(visible.isEmpty() ? null : visible.get(0));

        model.addAttribute("pageTitle", "History");
        model.addAttribute("query", query);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("action", action);
        model.addAttribute("actions", Action.values());
        model.addAttribute("actionTabs", List.of(Action.COMPLETED, Action.STATUS_CHANGED, Action.CREATED, Action.DELETED));
        model.addAttribute("timeline", "timeline".equals(view));
        model.addAttribute("view", "timeline".equals(view) ? "timeline" : "list");
        model.addAttribute("categories", categories.findAll());
        model.addAttribute("historyToday", today);
        model.addAttribute("yesterday", today.minusDays(1));
        model.addAttribute("groups", groups);
        model.addAttribute("earlierCount", visible.stream()
                .filter(item -> item.getOccurredAt().toLocalDate().isBefore(today.minusDays(1))).count());
        model.addAttribute("total", matching.size());
        model.addAttribute("completionCount", matching.stream().filter(item -> item.getAction() == Action.COMPLETED)
                .map(TaskActivity::getTaskId).distinct().count());
        model.addAttribute("statusCount", matching.stream().filter(item -> item.getAction() == Action.STATUS_CHANGED).count());
        model.addAttribute("rangeDays", !invalidRange && from != null && to != null ? ChronoUnit.DAYS.between(from, to) + 1 : null);
        model.addAttribute("selected", selected);
        if (invalidRange) {
            model.addAttribute("error", "End date cannot be before the start date");
        }
        if (selected != null) {
            addTaskPanel(model, selected, tab);
        }
        return "history";
    }

    private void addTaskPanel(Model model, TaskActivity selected, String tab) {
        List<TaskActivity> events = activity.findByTaskIdOrderByOccurredAtDescIdDesc(selected.getTaskId());
        boolean deleted = events.stream().anyMatch(item -> item.getAction() == Action.DELETED);
        LocalDateTime created = events.stream().filter(item -> item.getAction() == Action.CREATED)
                .map(TaskActivity::getOccurredAt).findFirst().orElse(null);
        LocalDateTime completed = events.stream().filter(item -> item.getAction() == Action.COMPLETED)
                .map(TaskActivity::getOccurredAt).findFirst().orElse(null);
        model.addAttribute("tab", switch (tab) {
            case "details", "related" -> tab;
            default -> "activity";
        });
        model.addAttribute("taskEvents", events);
        model.addAttribute("deleted", deleted);
        model.addAttribute("created", created);
        model.addAttribute("completed", completed);
        if (created != null && completed != null && !completed.isBefore(created)) {
            Duration elapsed = Duration.between(created, completed);
            model.addAttribute("duration", elapsed.toDays() > 0
                    ? elapsed.toDays() + " d " + elapsed.toHoursPart() + " hr"
                    : elapsed.toHours() + " hr " + elapsed.toMinutesPart() + " min");
        }
        if ("related".equals(tab)) {
            model.addAttribute("relatedTasks", tasks.search(null, selected.getCategoryId()).stream()
                    .filter(task -> !task.getId().equals(selected.getTaskId())).limit(8).toList());
        }
    }
}
