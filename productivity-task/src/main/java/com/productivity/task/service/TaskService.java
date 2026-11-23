package com.productivity.task.service;

import com.productivity.core.exception.ResourceNotFoundException;
import com.productivity.task.entity.Task;
import com.productivity.task.entity.TaskActivity;
import com.productivity.task.entity.TaskIteration;
import com.productivity.task.entity.TaskTimeLog;
import com.productivity.task.entity.TimerDailySeconds;
import com.productivity.task.enums.Priority;
import com.productivity.task.enums.TaskStatus;
import com.productivity.task.enums.TaskTimerStatus;
import com.productivity.task.repository.TaskIterationRepository;
import com.productivity.task.repository.TaskRepository;
import com.productivity.task.repository.TaskTimeLogRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskService {
    private final TaskRepository repository;
    private final TaskIterationRepository taskIterations;
    private final TaskTimeLogRepository timeLogs;

    public TaskService(TaskRepository repository) {
        this(repository, null, null);
    }

    public TaskService(TaskRepository repository, TaskIterationRepository taskIterations) {
        this(repository, taskIterations, null);
    }

    @Autowired
    public TaskService(TaskRepository repository,
                       @Autowired(required = false) TaskIterationRepository taskIterations,
                       @Autowired(required = false) TaskTimeLogRepository timeLogs) {
        this.repository = repository;
        this.taskIterations = taskIterations;
        this.timeLogs = timeLogs;
    }
    public Task get(Long id) { return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Task not found")); }
    public List<Task> search(String query, Long categoryId) { return repository.search(normalizeQuery(query), categoryId, null, null); }
    public List<Task> search(String query, Long categoryId, TaskStatus status, Priority priority) {
        return repository.search(normalizeQuery(query), categoryId, status, priority);
    }
    public List<Task> forDate(LocalDate date) { return repository.findByPlannedDateAndArchivedFalseOrderByPriorityDescCreatedAtAsc(date); }
    public List<Task> forBoard(LocalDate date) {
        LocalDate today = LocalDate.now();
        List<Task> boardTasks = date != null && date.equals(today)
                ? repository.findForBoardWithRollover(today, TaskStatus.COMPLETED,
                        today.atStartOfDay(), today.plusDays(1).atStartOfDay())
                : forDate(date);
        return boardTasks.stream()
                .sorted(Comparator.comparing(Task::getBoardPosition, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Transactional
    public void placeOnBoard(Long id, LocalDate date, TaskStatus status, Long before, Long after) {
        List<Task> boardTasks = forBoard(date);
        Task moved = boardTasks.stream().filter(task -> task.getId().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Task is no longer on this board"));

        List<Task> destination = new ArrayList<>(boardTasks.stream()
                .filter(task -> task.getStatus().getBoardColumn() == status.getBoardColumn() && !task.getId().equals(id))
                .toList());
        int position = destination.size();
        Long anchor = before != null ? before : after;
        if (anchor != null) {
            position = 0;
            while (position < destination.size() && !destination.get(position).getId().equals(anchor)) {
                position++;
            }
            if (position == destination.size()) {
                throw new IllegalArgumentException("Drop position is no longer available");
            }
            if (before == null) position++;
        }

        if (moved.getStatus() != status) changeStatus(id, status, null);
        destination.add(position, moved);
        for (int i = 0; i < destination.size(); i++) {
            destination.get(i).setBoardPosition(i);
        }
    }

    public List<Task> overdue() { return repository.findByDeadlineBeforeAndStatusNotAndArchivedFalseOrderByDeadlineAsc(LocalDate.now(), TaskStatus.COMPLETED); }
    public List<Task> completed(String query, Long categoryId, LocalDate from, LocalDate to) {
        LocalDateTime start = from == null ? LocalDate.of(1970, 1, 1).atStartOfDay() : from.atStartOfDay();
        LocalDateTime before = to == null ? LocalDate.of(9999, 12, 31).atStartOfDay() : to.plusDays(1).atStartOfDay();
        return repository.searchCompleted(normalizeQuery(query), categoryId, start, before);
    }

    @Transactional
    public Task save(Task task) {
        if (task.getDeadline() != null && task.getDeadline().isBefore(task.getPlannedDate())) {
            throw new IllegalArgumentException("Deadline cannot be earlier than the planned date");
        }
        synchronizeCompletion(task, task.getStatus());
        return repository.save(task);
    }

    @Transactional
    public Task save(Task task, TaskStatus status, String completionNote) {
        Task saved = save(task);
        if (!reopenCompletedTask(saved, status)) {
            saved.setStatus(status);
            saved.setCompletionNote(completionNote);
            synchronizeCompletion(saved, status);
        }
        return saved;
    }

    @Transactional
    public Task duplicate(Long id) {
        Task source = get(id);
        Task copy = new Task();
        String name = source.getName();
        String suffix = " (copy)";
        int limit = 140 - suffix.length();
        int length = Math.min(name.length(), limit);
        if (length < name.length() && Character.isHighSurrogate(name.charAt(length - 1))) {
            length--;
        }
        copy.setName(name.substring(0, length).stripTrailing() + suffix);
        copy.setDescription(source.getDescription());
        copy.setCategory(source.getCategory());
        copy.setPriority(source.getPriority());
        copy.setPlannedDate(source.getPlannedDate());
        copy.setDeadline(source.getDeadline());
        return save(copy);
    }

    @Transactional
    public void changeStatus(Long id, TaskStatus status, String completionNote) {
        Task task = get(id);
        if (reopenCompletedTask(task, status)) return;
        task.setStatus(status);
        if (completionNote != null && !completionNote.isBlank()) task.setCompletionNote(completionNote.trim());
        synchronizeCompletion(task, status);
        if (status == TaskStatus.COMPLETED) {
            if (task.getTimerStatus() != TaskTimerStatus.STOPPED) {
                stopTimer(task.getId(), "Completed task");
            }
            recordIterationCompletion(task, task.getIteration(), task.getCompletedAt(), task.getCompletionNote());
        }
    }

    @Transactional
    public Task lockForTimelineEdit(Long id) {
        return repository.findForTimelineEdit(id).orElseThrow(() -> new ResourceNotFoundException("Task not found"));
    }

    @Transactional
    public void reopenFromTimeline(Task task, LocalDateTime startedAt) {
        beginNewIteration(task, TaskStatus.IN_PROGRESS, startedAt);
        repository.flush();
    }

    @Transactional
    public void correctMilestoneDate(TaskActivity event, LocalDateTime correctedAt) {
        LocalDateTime previousAt = event.getOccurredAt();
        if (correctedAt.equals(previousAt)) return;

        Long taskId = event.getTaskId();
        if (event.getAction() == TaskActivity.Action.CREATED) {
            repository.correctCreatedAt(taskId, previousAt, correctedAt);
            if (taskIterations != null) {
                taskIterations.findByTaskIdAndIterationNumber(taskId, 1)
                        .filter(iteration -> previousAt.equals(iteration.getStartedAt()))
                        .ifPresent(iteration -> iteration.setStartedAt(correctedAt));
            }
        } else if (event.getAction() == TaskActivity.Action.COMPLETED) {
            Task task = get(taskId);
            if (previousAt.equals(task.getCompletedAt())) task.setCompletedAt(correctedAt);
            for (TaskIteration iteration : iterationsForTask(taskId)) {
                if (previousAt.equals(iteration.getCompletedAt())) iteration.setCompletedAt(correctedAt);
            }
        }

        event.setOccurredAt(correctedAt);
    }

    public List<TaskIteration> iterationsForTask(Long taskId) {
        return taskIterations != null ? taskIterations.findByTaskIdOrderByIterationNumberAsc(taskId) : List.of();
    }

    @Transactional
    public Task startRework(Long id, LocalDate newPlannedDate, LocalDate newDeadline) {
        Task task = get(id);
        if (newPlannedDate != null) {
            task.setPlannedDate(newPlannedDate);
        }
        if (newDeadline != null) {
            if (task.getPlannedDate() != null && newDeadline.isBefore(task.getPlannedDate())) {
                throw new IllegalArgumentException("Deadline cannot be earlier than planned date");
            }
            task.setDeadline(newDeadline);
        } else {
            task.setDeadline(null);
        }
        beginNewIteration(task, TaskStatus.IN_PROGRESS);
        return repository.save(task);
    }

    private boolean reopenCompletedTask(Task task, TaskStatus status) {
        if (task.getStatus() != TaskStatus.COMPLETED || status == TaskStatus.COMPLETED) {
            return false;
        }
        beginNewIteration(task, status);
        return true;
    }

    private void beginNewIteration(Task task, TaskStatus status) {
        beginNewIteration(task, status, LocalDateTime.now());
    }

    private void beginNewIteration(Task task, TaskStatus status, LocalDateTime startedAt) {
        int previousIteration = task.getIteration();
        recordIterationCompletion(task, previousIteration, task.getCompletedAt(), task.getCompletionNote());

        task.setIteration(previousIteration + 1);
        task.setStatus(status);
        task.setCompletedAt(null);
        task.setCompletionNote(null);

        if (taskIterations != null) {
            TaskIteration iteration = new TaskIteration();
            iteration.setTask(task);
            iteration.setIterationNumber(task.getIteration());
            iteration.setStartedAt(startedAt);
            taskIterations.save(iteration);
        }
    }

    private void recordIterationCompletion(Task task, int iteration, LocalDateTime completedAt, String note) {
        if (taskIterations == null) return;
        TaskIteration record = taskIterations.findByTaskIdAndIterationNumber(task.getId(), iteration)
                .orElseGet(() -> {
                    TaskIteration iter = new TaskIteration();
                    iter.setTask(task);
                    iter.setIterationNumber(iteration);
                    iter.setStartedAt(task.getCreatedAt() != null ? task.getCreatedAt() : LocalDateTime.now());
                    return iter;
                });
        record.setCompletedAt(completedAt != null ? completedAt : LocalDateTime.now());
        if (note != null && !note.isBlank()) record.setCompletionNote(note);
        taskIterations.save(record);
    }

    @Transactional
    public void archive(Long id) { get(id).setArchived(true); }

    @Transactional
    public void delete(Long id) {
        Task task = get(id);
        if (taskIterations != null) {
            taskIterations.deleteByTaskId(id);
        }
        if (timeLogs != null) {
            timeLogs.deleteByTaskId(id);
        }
        repository.delete(task);
    }

    @Transactional
    public Task startTimer(Long taskId) {
        Task task = get(taskId);
        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Move this task to In progress before starting its timer.");
        }
        if (task.getTimerStatus() == TaskTimerStatus.RUNNING) {
            return task;
        }

        LocalDateTime now = LocalDateTime.now();
        if (task.getTimerStatus() == TaskTimerStatus.STOPPED) {
            task.setTimerAccumulatedSeconds(0L);
            task.setTimerDailySeconds(java.util.Map.of());
            task.setTimerSessionStartedAt(now);
        } else {
            task.setTimerDailySeconds(task.getTimerDailySeconds());
            if (task.getTimerSessionStartedAt() == null) {
                LocalDateTime lastUpdated = task.getUpdatedAt() == null ? now : task.getUpdatedAt();
                task.setTimerSessionStartedAt(lastUpdated.minusSeconds(task.getTimerAccumulatedSeconds()));
            }
        }
        task.setTimerStatus(TaskTimerStatus.RUNNING);
        task.setTimerStartedAt(now);
        return repository.save(task);
    }

    @Transactional
    public Task pauseTimer(Long taskId) {
        Task task = get(taskId);
        if (task.getTimerStatus() != TaskTimerStatus.RUNNING) {
            return task;
        }
        pauseTaskTimer(task, LocalDateTime.now());
        return repository.save(task);
    }

    private void pauseTaskTimer(Task task, LocalDateTime now) {
        if (task.getTimerStatus() == TaskTimerStatus.RUNNING && task.getTimerStartedAt() != null) {
            if (task.getTimerSessionStartedAt() == null) {
                task.setTimerSessionStartedAt(task.getTimerStartedAt().minusSeconds(task.getTimerAccumulatedSeconds()));
            }
            var daily = task.getTimerDailySeconds();
            TimerDailySeconds.addInterval(daily, task.getTimerStartedAt(), now);
            task.setTimerDailySeconds(daily);
            task.setTimerAccumulatedSeconds(task.getActiveSessionSeconds(now));
        }
        task.setTimerStatus(TaskTimerStatus.PAUSED);
        task.setTimerStartedAt(null);
    }

    @Transactional
    public Task stopTimer(Long taskId, String note) {
        Task task = get(taskId);
        if (task.getTimerStatus() == TaskTimerStatus.STOPPED) {
            return task;
        }
        LocalDateTime now = LocalDateTime.now();
        long sessionSeconds = task.getActiveSessionSeconds(now);
        var daily = task.getTimerDailySeconds();
        if (task.getTimerStatus() == TaskTimerStatus.RUNNING) {
            TimerDailySeconds.addInterval(daily, task.getTimerStartedAt(), now);
        }
        if (sessionSeconds == 0) daily.merge(now.toLocalDate(), 1L, Long::sum);

        long recordedSeconds = Math.max(1, sessionSeconds);
        task.setTotalTimeSpentSeconds(task.getTotalTimeSpentSeconds() + recordedSeconds);
        if (timeLogs != null) {
            LocalDateTime startedAt = task.getTimerSessionStartedAt();
            if (startedAt == null) startedAt = now.minusSeconds(recordedSeconds);
            TaskTimeLog log = new TaskTimeLog(task, startedAt, now, recordedSeconds, note);
            log.setDailySeconds(daily);
            timeLogs.save(log);
        }

        task.setTimerStatus(TaskTimerStatus.STOPPED);
        task.setTimerStartedAt(null);
        task.setTimerAccumulatedSeconds(0L);
        task.setTimerDailySeconds(null);
        task.setTimerSessionStartedAt(null);
        return repository.save(task);
    }

    public List<TaskTimeLog> timeLogsForTask(Long taskId) {
        return timeLogs != null ? timeLogs.findByTaskIdOrderByStartedAtDesc(taskId) : List.of();
    }

    public long timerDayBaseSeconds(Task task, LocalDate day) {
        return task.getTimerDailySeconds().getOrDefault(day, 0L) + timeLogsForTask(task.getId()).stream()
                .mapToLong(log -> log.secondsOn(day)).sum();
    }

    public List<Task> getActiveTimers() {
        return repository.findByTimerStatus(TaskTimerStatus.RUNNING);
    }

    public Optional<Task> getActiveTimer() {
        return repository.findFirstByTimerStatus(TaskTimerStatus.RUNNING);
    }

    private void synchronizeCompletion(Task task, TaskStatus status) {
        if (status == TaskStatus.COMPLETED && task.getCompletedAt() == null) task.setCompletedAt(LocalDateTime.now());
        if (status != TaskStatus.COMPLETED) task.setCompletedAt(null);
    }

    private String normalizeQuery(String value) { return value == null ? "" : value.trim(); }
}
