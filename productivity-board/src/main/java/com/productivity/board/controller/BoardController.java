package com.productivity.board.controller;

import com.productivity.board.service.BoardService;
import com.productivity.category.service.CategoryService;
import com.productivity.task.entity.Task;
import com.productivity.task.entity.TimerDailySeconds;
import com.productivity.task.enums.TaskStatus;
import com.productivity.task.service.TaskService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class BoardController {
    private final BoardService board;
    private final CategoryService categories;
    private final TaskService tasks;

    public BoardController(BoardService board, CategoryService categories, TaskService tasks) {
        this.board = board;
        this.categories = categories;
        this.tasks = tasks;
    }

    @GetMapping("/board")
    public String board(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                        @RequestParam(required = false) Long category,
                        Model model) {
        LocalDate selected = date == null ? LocalDate.now() : date;
        var dayBoard = board.board(selected, category);
        int totalTasks = dayBoard.values().stream().mapToInt(java.util.List::size).sum();
        long completedTasks = dayBoard.getOrDefault(TaskStatus.COMPLETED, java.util.List.of()).stream()
                .filter(task -> task.getStatus() == TaskStatus.COMPLETED).count();
        int completionPct = totalTasks == 0 ? 0 : (int) Math.round(completedTasks * 100.0 / totalTasks);

        List<Task> activeTimers = tasks.getActiveTimers();
        Task activeTimer = activeTimers.isEmpty() ? null : activeTimers.get(0);
        model.addAttribute("boardActiveTimer", activeTimer);
        model.addAttribute("boardActiveTimers", activeTimers);
        model.addAttribute("boardActiveCount", activeTimers.size());

        LocalDateTime now = LocalDateTime.now();
        java.util.List<ActiveBoardTimerView> activeTimerViews = activeTimers.stream().map(t -> {
            long session = t.getActiveSessionSeconds(now);
            long todayBase = tasks.timerDayBaseSeconds(t, now.toLocalDate());
            long todaySeconds = todayBase + TimerDailySeconds.onDay(t.getTimerStartedAt(), now, now.toLocalDate());
            return new ActiveBoardTimerView(t, session, todaySeconds, todayBase,
                    Task.formatTimerDigits(session), Task.formatTimerDigits(todaySeconds));
        }).toList();
        model.addAttribute("boardActiveTimerViews", activeTimerViews);
        ZoneId zone = ZoneId.systemDefault();
        model.addAttribute("timerDayStart", now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli());
        model.addAttribute("timerNextDayStart", now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli());

        java.util.Set<Long> runningIds = activeTimers.stream().map(Task::getId).collect(java.util.stream.Collectors.toSet());
        model.addAttribute("boardTimerCandidates", dayBoard.entrySet().stream()
                .filter(entry -> entry.getKey() == TaskStatus.IN_PROGRESS)
                .flatMap(entry -> entry.getValue().stream())
                .filter(task -> !runningIds.contains(task.getId()))
                .toList());

        if (activeTimer != null && !activeTimerViews.isEmpty()) {
            ActiveBoardTimerView firstView = activeTimerViews.get(0);
            model.addAttribute("boardActiveSessionSeconds", firstView.sessionSeconds());
            model.addAttribute("boardActiveTodaySeconds", firstView.todaySeconds());
            model.addAttribute("boardActiveClock", firstView.sessionClock());
            model.addAttribute("boardActiveTodayClock", firstView.todayClock());
        }

        model.addAttribute("pageTitle", "Daily board");
        model.addAttribute("selectedDate", selected);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("selectedCategory", category);
        model.addAttribute("categories", categories.findActive());
        model.addAttribute("board", dayBoard);
        model.addAttribute("statuses", java.util.List.of(TaskStatus.YET_TO_START, TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED));
        model.addAttribute("totalTasks", totalTasks);
        model.addAttribute("completedTasks", completedTasks);
        model.addAttribute("boardCompletionPct", completionPct);
        return "board";
    }

    @PostMapping(path = "/board/tasks/{id}/move", params = "!date")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> moveTask(
            @PathVariable Long id,
            @RequestParam TaskStatus status,
            @RequestParam(required = false) String completionNote) {
        tasks.changeStatus(id, status, completionNote);
        return moved(id, status);
    }

    @PostMapping(path = "/board/tasks/{id}/move", params = "date")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> placeTask(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam TaskStatus status,
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Long after) {
        tasks.placeOnBoard(id, date, status, before, after);
        return moved(id, status);
    }

    private ResponseEntity<Map<String, Object>> moved(Long id, TaskStatus status) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "id", id,
                "status", status.name(),
                "statusLabel", status.getLabel()
        ));
    }

    public record ActiveBoardTimerView(Task task, long sessionSeconds, long todaySeconds, long todayBaseSeconds, String sessionClock, String todayClock) {}
}
