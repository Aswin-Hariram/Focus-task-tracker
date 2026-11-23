package com.productivity.core.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
        int status,
        String error,
        String message,
        List<String> details,
        LocalDateTime timestamp,
        String path
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(status, error, message, List.of(), LocalDateTime.now(), path);
    }

    public static ErrorResponse of(int status, String error, String message, List<String> details, String path) {
        return new ErrorResponse(status, error, message, details != null ? details : List.of(), LocalDateTime.now(), path);
    }
}
