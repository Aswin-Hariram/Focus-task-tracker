package com.productivity.analytics.service;

import com.productivity.task.entity.Task;
import com.productivity.task.enums.TaskStatus;
import com.productivity.task.repository.TaskRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {
    private final TaskRepository tasks;
    public AnalyticsService(TaskRepository tasks) { this.tasks = tasks; }

    public Summary summary() {
        long total = tasks.countByArchivedFalse();
        long completed = tasks.countByStatusAndArchivedFalse(TaskStatus.COMPLETED);
        long inProgress = tasks.countByStatusAndArchivedFalse(TaskStatus.IN_PROGRESS);
        long pending = tasks.countByStatusAndArchivedFalse(TaskStatus.YET_TO_START);
        long overdue = tasks.findByDeadlineBeforeAndStatusNotAndArchivedFalseOrderByDeadlineAsc(LocalDate.now(), TaskStatus.COMPLETED).size();
        int completionRate = total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);
        return new Summary(total, completed, inProgress, pending, overdue, completionRate);
    }

    public List<TrendPoint> completionTrend(int days) {
        List<TrendPoint> points = new ArrayList<>();
        for (int offset = days - 1; offset >= 0; offset--) {
            LocalDate date = LocalDate.now().minusDays(offset);
            long count = tasks.countByCompletedAtBetween(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
            points.add(new TrendPoint(date, count));
        }
        return points;
    }

    public Map<String, Long> categoryDistribution() {
        Map<String, Long> result = new LinkedHashMap<>();
        tasks.countByCategory().forEach(row -> result.put((String) row[0], (Long) row[1]));
        return result;
    }

    public List<PeriodSummary> periodSummaries() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(6);
        LocalDate monthStart = today.withDayOfMonth(1);
        return List.of(
                summarize("Today", today, today),
                summarize("Last 7 days", weekStart, today),
                summarize("This month", monthStart, today)
        );
    }

    public List<CategorySummary> categorySummaries() {
        return tasks.completionByCategory().stream().map(row -> {
            long total = ((Number) row[1]).longValue();
            long completed = ((Number) row[2]).longValue();
            int rate = total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);
            return new CategorySummary((String) row[0], total, completed, rate);
        }).toList();
    }

    private static final List<String> CATEGORY_COLORS = List.of(
            "#2276f5", "#8b4ef4", "#20a965", "#e9931d", "#d64e87", "#1596a6", "#687c9c");

    public Report report(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo, LocalDate today) {
        List<Task> all = tasks.search(null, null, null, null);
        var planned = new HashMap<LocalDate, List<Task>>();
        var completionDays = new HashMap<LocalDate, Long>();
        for (Task task : all) {
            planned.computeIfAbsent(task.getPlannedDate(), date -> new ArrayList<>()).add(task);
            if (task.getStatus() == TaskStatus.COMPLETED && task.getCompletedAt() != null) {
                LocalDate completedOn = task.getCompletedAt().toLocalDate();
                if (!completedOn.isAfter(today)) {
                    completionDays.merge(completedOn, 1L, Long::sum);
                }
            }
        }

        var selected = all.stream().filter(task -> inRange(task.getPlannedDate(), from, to)).toList();
        var previous = all.stream().filter(task -> inRange(task.getPlannedDate(), previousFrom, previousTo)).toList();
        long completed = completedCount(selected);
        long previouslyCompleted = completedCount(previous);
        int rate = percent(completed, selected.size());

        var categoryCounts = selected.stream().collect(Collectors.groupingBy(
                task -> task.getCategory() == null ? "Uncategorized" : task.getCategory().getName(),
                Collectors.counting()));
        var categories = new ArrayList<CategorySlice>();
        var stops = new ArrayList<String>();
        long cumulative = 0;
        for (var entry : categoryCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .toList()) {
            String color = CATEGORY_COLORS.get(categories.size() % CATEGORY_COLORS.size());
            double start = cumulative * 100.0 / selected.size();
            cumulative += entry.getValue();
            double end = cumulative * 100.0 / selected.size();
            stops.add(color + " " + start + "% " + end + "%");
            categories.add(new CategorySlice(entry.getKey(), entry.getValue(), percent(entry.getValue(), selected.size()), color));
        }

        long days = ChronoUnit.DAYS.between(from, to) + 1;
        long bucketDays = days <= 7 ? 1 : days <= 42 ? 7 : (days + 9) / 10;
        var progress = new ArrayList<ProgressPoint>();
        var labels = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
        long runningTotal = 0;
        long runningCompleted = 0;
        for (LocalDate start = from; !start.isAfter(to); start = start.plusDays(bucketDays)) {
            LocalDate end = start.plusDays(Math.min(bucketDays - 1, ChronoUnit.DAYS.between(start, to)));
            long total = 0;
            long done = 0;
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                var dayTasks = planned.getOrDefault(date, List.of());
                total += dayTasks.size();
                done += completedCount(dayTasks);
            }
            runningTotal += total;
            runningCompleted += done;
            progress.add(new ProgressPoint(labels.format(start),
                    start.equals(end) ? labels.format(start) : labels.format(start) + " – " + labels.format(end),
                    total, done, percent(runningCompleted, runningTotal)));
        }
        long chartMax = Math.max(4, progress.stream().mapToLong(ProgressPoint::planned).max().orElse(0));
        long axisMax = ((chartMax + 3) / 4) * 4;
        var coordinates = new ArrayList<String>();
        for (int i = 0; i < progress.size(); i++) {
            double x = progress.size() == 1 ? 50 : i * 100.0 / (progress.size() - 1);
            coordinates.add(x + "," + (100 - progress.get(i).rate()));
        }

        int bestStreak = 0;
        int run = 0;
        LocalDate last = null;
        for (LocalDate date : new TreeSet<>(completionDays.keySet())) {
            run = last != null && last.plusDays(1).equals(date) ? run + 1 : 1;
            bestStreak = Math.max(bestStreak, run);
            last = date;
        }
        int streak = last != null && !last.isBefore(today.minusDays(1)) ? run : 0;
        var bestDay = completionDays.entrySet().stream()
                .filter(entry -> inRange(entry.getKey(), from, to))
                .max(Map.Entry.<LocalDate, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()));
        return new Report(selected.size(), completed, rate, streak, bestStreak,
                change(selected.size(), previous.size()), change(completed, previouslyCompleted),
                rate - percent(previouslyCompleted, previous.size()), !previous.isEmpty(),
                progress, axisMax, String.join(" ", coordinates), categories, String.join(", ", stops),
                bestDay.map(entry -> entry.getKey().format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH))).orElse(""),
                bestDay.map(Map.Entry::getValue).orElse(0L));
    }

    private static boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
        return !date.isBefore(from) && !date.isAfter(to);
    }

    private static long completedCount(List<Task> tasks) {
        return tasks.stream().filter(task -> task.getStatus() == TaskStatus.COMPLETED).count();
    }

    private static int percent(long count, long total) {
        return total == 0 ? 0 : (int) Math.round(count * 100.0 / total);
    }

    private static String change(long count, long previous) {
        if (previous == 0) return count == 0 ? "0%" : "No prior count";
        long delta = Math.round((count - previous) * 100.0 / previous);
        return (delta > 0 ? "+" : "") + delta + "%";
    }

    public record ProgressPoint(String label, String range, long planned, long completed, int rate) {}
    public record CategorySlice(String name, long count, int percent, String color) {}
    public record Report(long total, long completed, int rate, int streak, int bestStreak,
                         String totalChange, String completedChange, int rateChange, boolean hasPrevious,
                         List<ProgressPoint> progress, long axisMax, String trendPoints,
                         List<CategorySlice> categories, String categoryStops, String bestDay, long bestDayCount) {}

    private PeriodSummary summarize(String label, LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime before = to.plusDays(1).atStartOfDay();
        long created = tasks.countByCreatedAtBetweenAndArchivedFalse(start, before);
        long completed = tasks.countByCompletedAtBetween(start, before);
        return new PeriodSummary(label, created, completed);
    }

    public record Summary(long total, long completed, long inProgress, long pending, long overdue, int completionRate) {}
    public record TrendPoint(LocalDate date, long completed) {}
    public record PeriodSummary(String label, long created, long completed) {}
    public record CategorySummary(String category, long total, long completed, int completionRate) {}
}
