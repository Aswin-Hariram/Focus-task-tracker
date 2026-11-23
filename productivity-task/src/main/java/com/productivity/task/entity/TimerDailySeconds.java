package com.productivity.task.entity;

import jakarta.persistence.AttributeConverter;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class TimerDailySeconds implements AttributeConverter<Map<LocalDate, Long>, String> {
    @Override
    public String convertToDatabaseColumn(Map<LocalDate, Long> totals) {
        if (totals == null) return null;
        return totals.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(","));
    }

    @Override
    public Map<LocalDate, Long> convertToEntityAttribute(String value) {
        if (value == null) return null;
        Map<LocalDate, Long> totals = new HashMap<>();
        if (!value.isEmpty()) {
            for (String entry : value.split(",")) {
                String[] parts = entry.split("=", 2);
                totals.put(LocalDate.parse(parts[0]), Long.parseLong(parts[1]));
            }
        }
        return totals;
    }

    public static long onDay(LocalDateTime start, LocalDateTime end, LocalDate day) {
        if (start == null || end == null || !end.isAfter(start)) return 0;
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime nextDay = day.plusDays(1).atStartOfDay();
        LocalDateTime from = start.isAfter(dayStart) ? start : dayStart;
        LocalDateTime to = end.isBefore(nextDay) ? end : nextDay;
        if (!to.isAfter(from)) return 0;
        return Duration.between(start, to).getSeconds() - Duration.between(start, from).getSeconds();
    }

    public static void addInterval(Map<LocalDate, Long> totals, LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) return;
        for (LocalDate day = start.toLocalDate(); !day.isAfter(end.toLocalDate()); day = day.plusDays(1)) {
            long seconds = onDay(start, end, day);
            if (seconds > 0) totals.merge(day, seconds, Long::sum);
        }
    }
}
