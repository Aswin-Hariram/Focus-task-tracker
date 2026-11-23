package com.productivity.task.enums;

public enum TaskStatus {
    YET_TO_START("Yet to start"),
    IN_PROGRESS("In progress"),
    COMPLETED("Completed"),
    BLOCKED("Blocked/Closed");

    private final String label;
    TaskStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
    public TaskStatus getBoardColumn() { return this == BLOCKED ? COMPLETED : this; }
}
