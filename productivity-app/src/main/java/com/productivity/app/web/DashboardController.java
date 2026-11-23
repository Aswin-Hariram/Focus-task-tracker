package com.productivity.app.web;

import com.productivity.attachment.repository.AttachmentRepository;
import com.productivity.core.common.DateTimeUtils;
import com.productivity.progress.repository.ProgressUpdateRepository;
import com.productivity.task.entity.Task;
import com.productivity.task.enums.TaskStatus;
import com.productivity.task.repository.TaskRepository;
import com.productivity.task.service.TaskService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController {
    private final TaskService tasks;
    private final TaskRepository taskRepository;
    private final AttachmentRepository attachmentRepository;
    private final ProgressUpdateRepository progressUpdateRepository;

    @Autowired
    public DashboardController(TaskService tasks,
                               @Autowired(required = false) TaskRepository taskRepository,
                               @Autowired(required = false) AttachmentRepository attachmentRepository,
                               @Autowired(required = false) ProgressUpdateRepository progressUpdateRepository) {
        this.tasks = tasks;
        this.taskRepository = taskRepository;
        this.attachmentRepository = attachmentRepository;
        this.progressUpdateRepository = progressUpdateRepository;
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            Model model) {
        LocalDate today = LocalDate.now();
        LocalDate selectedDate = date != null ? date : today;
        boolean isToday = selectedDate.equals(today);
        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("today", today);
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("isToday", isToday);

        // Dynamic greeting based on current time
        int hour = LocalTime.now().getHour();
        String greetingPrefix = (hour >= 5 && hour < 12) ? "Good Morning" : (hour >= 12 && hour < 17) ? "Good Afternoon" : "Good Evening";
        model.addAttribute("greeting", greetingPrefix + ", Aswin! 👋");
        model.addAttribute("userName", "Aswin");

        LocalDate openingWeek = isToday ? calendarStart(today)
                : selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate monthStart = selectedDate.withDayOfMonth(1);
        LocalDate monthEnd = selectedDate.with(TemporalAdjusters.lastDayOfMonth());
        LocalDate start = monthStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = monthEnd.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        if (openingWeek.isBefore(start)) openingWeek = start;
        LocalDate openingEnd = openingWeek.plusDays(13);
        if (openingEnd.isAfter(end)) end = openingEnd;
        Set<LocalDate> scheduledDates = taskRepository == null ? Set.of()
                : Set.copyOf(taskRepository.findActivePlannedDates(monthStart, monthEnd));

        List<List<DayView>> calendarWeeks = new ArrayList<>();
        for (LocalDate monday = start; !monday.isAfter(end); monday = monday.plusWeeks(1)) {
            List<DayView> days = new ArrayList<>(7);
            for (int offset = 0; offset < 7; offset++) {
                LocalDate day = monday.plusDays(offset);
                boolean isCurrentDayToday = day.equals(today);
                boolean isCurrentDaySelected = day.equals(selectedDate);
                days.add(new DayView(day, day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                        day.getDayOfMonth(), isCurrentDayToday, isCurrentDaySelected, scheduledDates.contains(day)));
            }
            calendarWeeks.add(days);
        }
        model.addAttribute("calendarWeeks", calendarWeeks);
        model.addAttribute("calendarOpeningWeek", openingWeek);
        model.addAttribute("weekMonthYear", selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)));
        model.addAttribute("prevWeekDate", selectedDate.minusWeeks(1));
        model.addAttribute("nextWeekDate", selectedDate.plusWeeks(1));

        // Analytics summary
        model.addAttribute("summary", buildSummary());

        // Board tasks for selected date
        var todayBoard = buildTodayBoard(selectedDate);
        model.addAttribute("todayBoard", todayBoard);
        List<Task> inProgress = todayBoard.getOrDefault(TaskStatus.IN_PROGRESS, List.of());
        List<Task> yetToStart = todayBoard.getOrDefault(TaskStatus.YET_TO_START, List.of());
        List<Task> completed = todayBoard.getOrDefault(TaskStatus.COMPLETED, List.of());

        List<Task> todayAllTasks = new ArrayList<>();
        todayAllTasks.addAll(inProgress);
        todayAllTasks.addAll(yetToStart);
        todayAllTasks.addAll(completed);

        model.addAttribute("todayTasks", todayAllTasks);
        model.addAttribute("todayCount", todayAllTasks.size());
        model.addAttribute("inProgressTasks", inProgress);
        model.addAttribute("inboxTasks", yetToStart);
        model.addAttribute("completedTasks", completed);

        // Tomorrow (next day) and next week counts and tasks relative to selectedDate
        LocalDate tomorrow = selectedDate.plusDays(1);
        List<Task> tomorrowTasks = tasks != null ? tasks.forDate(tomorrow) : List.of();
        model.addAttribute("tomorrowTasks", tomorrowTasks);
        model.addAttribute("tomorrowCount", tomorrowTasks.size());

        List<Task> nextWeekTasks = new ArrayList<>();
        if (tasks != null) {
            for (int i = 2; i <= 8; i++) {
                nextWeekTasks.addAll(tasks.forDate(selectedDate.plusDays(i)));
            }
        }
        model.addAttribute("nextWeekTasks", nextWeekTasks);
        model.addAttribute("nextWeekCount", nextWeekTasks.size());

        // Overdue preview & trend
        model.addAttribute("overduePreview", buildOverduePreview(today));
        model.addAttribute("statuses", TaskStatus.values());
        model.addAttribute("trend", buildTrend(7));
        return "dashboard";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("pageTitle", "Settings");
        return "settings";
    }

    private Map<TaskStatus, List<Task>> buildTodayBoard(LocalDate today) {
        List<Task> dayTasks = tasks != null ? tasks.forBoard(today) : List.of();
        for (Task task : dayTasks) {
            if (attachmentRepository != null) {
                task.setAttachmentCount((int) attachmentRepository.countByTaskId(task.getId()));
            }
            if (progressUpdateRepository != null) {
                task.setProgressUpdateCount((int) progressUpdateRepository.countByTaskId(task.getId()));
            }
        }
        Map<TaskStatus, List<Task>> board = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            board.put(status, dayTasks.stream().filter(task -> task.getStatus() == status).toList());
        }
        return board;
    }

    private SummaryView buildSummary() {
        if (taskRepository != null) {
            long total = taskRepository.countByArchivedFalse();
            long completed = taskRepository.countByStatusAndArchivedFalse(TaskStatus.COMPLETED);
            long inProgress = taskRepository.countByStatusAndArchivedFalse(TaskStatus.IN_PROGRESS);
            long pending = taskRepository.countByStatusAndArchivedFalse(TaskStatus.YET_TO_START);
            long overdue = taskRepository.findByDeadlineBeforeAndStatusNotAndArchivedFalseOrderByDeadlineAsc(LocalDate.now(), TaskStatus.COMPLETED).size();
            int completionRate = total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);
            return new SummaryView(total, completed, inProgress, pending, overdue, completionRate);
        }
        return new SummaryView(0, 0, 0, 0, 0, 0);
    }

    private List<OverdueItem> buildOverduePreview(LocalDate today) {
        if (tasks == null) {
            return List.of();
        }
        return tasks.overdue().stream()
                .limit(5)
                .map(task -> new OverdueItem(task, DateTimeUtils.daysOverdue(task.getDeadline(), today)))
                .toList();
    }

    private List<TrendPoint> buildTrend(int days) {
        List<TrendPoint> points = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int offset = days - 1; offset >= 0; offset--) {
            LocalDate date = today.minusDays(offset);
            long count = (taskRepository != null)
                    ? taskRepository.countByCompletedAtBetween(date.atStartOfDay(), date.plusDays(1).atStartOfDay())
                    : 0;
            points.add(new TrendPoint(date, count));
        }
        return points;
    }

    static LocalDate calendarStart(LocalDate today) {
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return today.getDayOfWeek().getValue() <= DayOfWeek.WEDNESDAY.getValue()
                ? monday.minusWeeks(1) : monday;
    }

    public record DayView(LocalDate date, String dayName, int dayNumber, boolean isToday, boolean isSelected, boolean hasTasks) {}
    public record SummaryView(long total, long completed, long inProgress, long pending, long overdue, int completionRate) {}
    public record OverdueItem(Task task, long daysOverdue) {}
    public record TrendPoint(LocalDate date, long count) {}
}
