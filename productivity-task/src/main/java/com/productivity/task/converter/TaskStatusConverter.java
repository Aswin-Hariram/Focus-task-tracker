package com.productivity.task.converter;

import com.productivity.task.enums.TaskStatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class TaskStatusConverter implements Converter<String, TaskStatus> {
    @Override
    public TaskStatus convert(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String val = source.trim().toUpperCase().replace('/', '_').replace('-', '_').replace(' ', '_');
        if ("BLOCKED".equals(val) || "CLOSED".equals(val) || "BLOCKED_CLOSED".equals(val)) {
            return TaskStatus.BLOCKED;
        }
        for (TaskStatus status : TaskStatus.values()) {
            if (status.name().equalsIgnoreCase(val) || status.getLabel().equalsIgnoreCase(source.trim())) {
                return status;
            }
        }
        return TaskStatus.valueOf(val);
    }
}
