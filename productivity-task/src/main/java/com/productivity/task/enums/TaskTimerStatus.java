package com.productivity.task.enums;

public enum TaskTimerStatus {
    STOPPED("Stopped"),
    RUNNING("Running"),
    PAUSED("Paused");

    private final String label;

    TaskTimerStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
