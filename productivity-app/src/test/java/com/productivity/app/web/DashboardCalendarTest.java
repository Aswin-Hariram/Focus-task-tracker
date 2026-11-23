package com.productivity.app.web;

import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardCalendarTest {
    @ParameterizedTest
    @CsvSource({
        "2026-11-23, 2026-11-16",
        "2026-11-24, 2026-11-16",
        "2026-11-25, 2026-11-16",
        "2026-11-26, 2026-11-23",
        "2026-11-29, 2026-11-23"
    })
    void calendarSwitchesWeeksAfterWednesday(LocalDate today, LocalDate firstMonday) {
        assertThat(DashboardController.calendarStart(today)).isEqualTo(firstMonday);
    }
}
