package com.productivity.progress.service;

import com.productivity.progress.entity.ProgressUpdate;
import com.productivity.progress.repository.ProgressUpdateRepository;
import com.productivity.core.exception.ResourceNotFoundException;
import com.productivity.task.entity.Task;
import com.productivity.task.service.TaskService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProgressService {
    private final ProgressUpdateRepository repository;
    private final TaskService tasks;

    public ProgressService(ProgressUpdateRepository repository, TaskService tasks) {
        this.repository = repository;
        this.tasks = tasks;
    }

    public List<ProgressUpdate> forTask(Long taskId) { return repository.findByTaskIdOrderByRecordedAtDesc(taskId); }
    public List<ProgressUpdate> forTaskAndIteration(Long taskId, int iteration) {
        return repository.findByTaskIdAndIterationOrderByRecordedAtDesc(taskId, iteration);
    }
    public ProgressUpdate get(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Progress update not found"));
    }
    public ProgressUpdate requireBelongsToTask(Long id, Long taskId) {
        ProgressUpdate update = get(id);
        if (!update.getTask().getId().equals(taskId)) {
            throw new IllegalArgumentException("Progress update does not belong to this task");
        }
        return update;
    }

    @Transactional
    public ProgressUpdate add(Long taskId, ProgressUpdate update) {
        Task task = tasks.get(taskId);
        update.setTask(task);
        if (update.getRecordedAt() == null) {
            update.setRecordedAt(LocalDateTime.now());
        }
        update.setIteration(task.getIteration());
        return repository.save(update);
    }

    @Transactional
    public void edit(Long taskId, Long updateId, String description, String reminder) {
        edit(taskId, updateId, description, reminder, null);
    }

    @Transactional
    public void edit(Long taskId, Long updateId, String description, String reminder, LocalDateTime recordedAt) {
        ProgressUpdate update = requireBelongsToTask(updateId, taskId);
        applyDetails(update, description, reminder);
        if (recordedAt != null) {
            update.setRecordedAt(recordedAt);
        }
        repository.save(update);
    }

    @Transactional
    public ProgressUpdate addForActivity(Long taskId, LocalDateTime occurredAt, String description, String reminder) {
        ProgressUpdate update = new ProgressUpdate();
        applyDetails(update, description, reminder);
        update.setRecordedAt(occurredAt != null ? occurredAt : LocalDateTime.now());
        Task task = tasks.get(taskId);
        update.setTask(task);
        var iterations = tasks.iterationsForTask(taskId);
        int iteration = iterations.stream()
                .filter(item -> !item.getStartedAt().isAfter(update.getRecordedAt()))
                .mapToInt(item -> item.getIterationNumber()).max().orElse(1);
        update.setIteration(iterations.isEmpty() ? task.getIteration() : iteration);
        return repository.save(update);
    }

    private void applyDetails(ProgressUpdate update, String description, String reminder) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Describe the progress made");
        }
        if (description.length() > 3000 || (reminder != null && reminder.length() > 1500)) {
            throw new IllegalArgumentException("Use up to 3,000 characters for progress and 1,500 for the reminder");
        }
        update.setDescription(description.strip());
        update.setReminder(reminder == null || reminder.isBlank() ? null : reminder.strip());
    }

    @Transactional
    public void delete(Long id) {
        ProgressUpdate update = get(id);
        repository.delete(update);
    }

    @Transactional
    public void delete(Long taskId, Long updateId) {
        ProgressUpdate update = requireBelongsToTask(updateId, taskId);
        repository.delete(update);
    }
}
