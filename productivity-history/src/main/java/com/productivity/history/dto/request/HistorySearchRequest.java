package com.productivity.history.dto.request;

import java.time.LocalDate;

public record HistorySearchRequest(
        String query,
        Long categoryId,
        LocalDate from,
        LocalDate to
) {}
