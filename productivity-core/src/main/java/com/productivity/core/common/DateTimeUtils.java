package com.productivity.core.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

public final class DateTimeUtils {
    private DateTimeUtils() {}

    private static final DateTimeFormatter DISPLAY_DATE_FORMATTER =
            DateTimeFormatter.ofPattern(AppConstants.DISPLAY_DATE_FORMAT);
    private static final DateTimeFormatter DISPLAY_DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern(AppConstants.DISPLAY_DATETIME_FORMAT);

    public static String formatDisplayDate(LocalDate date) {
        return date == null ? "" : date.format(DISPLAY_DATE_FORMATTER);
    }

    public static String formatDisplayDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.format(DISPLAY_DATETIME_FORMATTER);
    }

    public static String formatIsoDate(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    public static long daysBetween(LocalDate start, LocalDate end) {
        if (start == null || end == null) return 0;
        return ChronoUnit.DAYS.between(start, end);
    }

    public static long daysOverdue(LocalDate deadline, LocalDate currentDate) {
        if (deadline == null || currentDate == null || !deadline.isBefore(currentDate)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(deadline, currentDate);
    }

    public static boolean isOverdue(LocalDate deadline, LocalDate currentDate) {
        return deadline != null && currentDate != null && deadline.isBefore(currentDate);
    }

    public static LocalDateTime startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    public static LocalDateTime endOfDay(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay().minusNanos(1);
    }

    public static LocalDate startOfWeek(LocalDate date) {
        if (date == null) return null;
        return date.minusDays(date.getDayOfWeek().getValue() - 1);
    }

    public static LocalDate startOfMonth(LocalDate date) {
        if (date == null) return null;
        return date.with(TemporalAdjusters.firstDayOfMonth());
    }

    public static LocalDate endOfMonth(LocalDate date) {
        if (date == null) return null;
        return date.with(TemporalAdjusters.lastDayOfMonth());
    }
}
