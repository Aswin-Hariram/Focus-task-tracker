package com.productivity.core.common;

import java.util.Set;

public final class AppConstants {
    private AppConstants() {}

    public static final String DEFAULT_STORAGE_ROOT = "./storage";
    public static final String TASK_ATTACHMENTS_DIR = "task-attachments";
    public static final String PROGRESS_ATTACHMENTS_DIR = "progress-attachments";
    public static final String EXPORTS_DIR = "exports";

    public static final long MAX_ATTACHMENT_BYTES = -1L; // Unlimited upload size
    public static final Set<String> ALLOWED_ATTACHMENT_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "text/plain",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String DISPLAY_DATE_FORMAT = "d MMM yyyy";
    public static final String DISPLAY_DATETIME_FORMAT = "d MMM yyyy, HH:mm";
}
