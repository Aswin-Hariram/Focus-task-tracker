package com.productivity.analytics.controller;

import com.productivity.analytics.service.AnalyticsService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AnalyticsController {
    private final AnalyticsService analytics;
    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/analytics")
    public String analytics(Model model, @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = LocalDate.now();
        LocalDate start;
        LocalDate end;
        LocalDate previousStart;
        String rangeError = "";
        if ("custom".equals(period) && (from == null || to == null || to.isBefore(from)
                || ChronoUnit.DAYS.between(from, to) > 365)) {
            rangeError = "Choose a start and end date in order, spanning at most 366 days.";
            period = "week";
        }
        switch (period) {
            case "month" -> {
                start = today.withDayOfMonth(1);
                end = start.plusMonths(1).minusDays(1);
                previousStart = start.minusMonths(1);
            }
            case "quarter" -> {
                int firstMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                start = LocalDate.of(today.getYear(), firstMonth, 1);
                end = start.plusMonths(3).minusDays(1);
                previousStart = start.minusMonths(3);
            }
            case "year" -> {
                start = today.withDayOfYear(1);
                end = start.plusYears(1).minusDays(1);
                previousStart = start.minusYears(1);
            }
            case "custom" -> {
                start = from;
                end = to;
                previousStart = start.minusDays(ChronoUnit.DAYS.between(start, end) + 1);
            }
            default -> {
                period = "week";
                start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                end = start.plusDays(6);
                previousStart = start.minusWeeks(1);
            }
        }
        model.addAttribute("pageTitle", "Analytics");
        model.addAttribute("period", period);
        model.addAttribute("from", start);
        model.addAttribute("to", end);
        model.addAttribute("rangeError", rangeError);
        model.addAttribute("report", analytics.report(start, end, previousStart, start.minusDays(1), today));
        return "analytics";
    }
}
