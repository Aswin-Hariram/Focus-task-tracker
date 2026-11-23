package com.productivity.core.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DateTimeUtilsTest {

    @Test
    void testFormatDisplayDate() {
        LocalDate date = LocalDate.of(2026, 11, 21);
        assertThat(DateTimeUtils.formatDisplayDate(date)).isEqualTo("21 Nov 2026");
        assertThat(DateTimeUtils.formatDisplayDate(null)).isEmpty();
    }

    @Test
    void testFormatDisplayDateTime() {
        LocalDateTime dateTime = LocalDateTime.of(2026, 11, 21, 14, 30);
        assertThat(DateTimeUtils.formatDisplayDateTime(dateTime)).isEqualTo("21 Nov 2026, 14:30");
        assertThat(DateTimeUtils.formatDisplayDateTime(null)).isEmpty();
    }

    @Test
    void testDaysBetweenAndOverdue() {
        LocalDate start = LocalDate.of(2026, 11, 15);
        LocalDate end = LocalDate.of(2026, 11, 21);
        assertThat(DateTimeUtils.daysBetween(start, end)).isEqualTo(6);

        LocalDate deadline = LocalDate.of(2026, 11, 18);
        LocalDate current = LocalDate.of(2026, 11, 21);
        assertThat(DateTimeUtils.isOverdue(deadline, current)).isTrue();
        assertThat(DateTimeUtils.daysOverdue(deadline, current)).isEqualTo(3);

        LocalDate futureDeadline = LocalDate.of(2026, 11, 25);
        assertThat(DateTimeUtils.isOverdue(futureDeadline, current)).isFalse();
        assertThat(DateTimeUtils.daysOverdue(futureDeadline, current)).isEqualTo(0);
    }

    @Test
    void testDateRangeHelpers() {
        LocalDate date = LocalDate.of(2026, 11, 21); // Saturday
        assertThat(DateTimeUtils.startOfDay(date)).isEqualTo(LocalDateTime.of(2026, 11, 21, 0, 0));
        assertThat(DateTimeUtils.startOfMonth(date)).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(DateTimeUtils.endOfMonth(date)).isEqualTo(LocalDate.of(2026, 11, 30));
    }
}
