package com.productivity.task.service;

import com.productivity.category.entity.Category;
import com.productivity.core.exception.ResourceNotFoundException;
import com.productivity.task.entity.Task;
import com.productivity.task.entity.TaskTimeLog;
import com.productivity.task.enums.Priority;
import com.productivity.task.enums.TaskStatus;
import com.productivity.task.enums.TaskTimerStatus;
import com.productivity.task.repository.TaskRepository;
import com.productivity.task.repository.TaskTimeLogRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository repository;

    @Mock
    private com.productivity.task.repository.TaskIterationRepository taskIterations;

    @Mock
    private TaskTimeLogRepository timeLogs;

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(repository, taskIterations, timeLogs);
    }

    @Test
    void testGetFoundAndNotFound() {
        Task task = new Task();
        task.setId(10L);
        task.setName("Write documentation");
        when(repository.findById(10L)).thenReturn(Optional.of(task));
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.get(10L).getName()).isEqualTo("Write documentation");
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void testSaveValidTask() {
        Task task = new Task();
        task.setName("Deploy update");
        task.setPlannedDate(LocalDate.now());
        task.setDeadline(LocalDate.now().plusDays(2));
        task.setStatus(TaskStatus.YET_TO_START);
        when(repository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Task saved = service.save(task);
        assertThat(saved.getName()).isEqualTo("Deploy update");
    }

    @Test
    void testSaveTaskWithDeadlineBeforePlannedDateThrows() {
        Task task = new Task();
        task.setName("Invalid task");
        task.setPlannedDate(LocalDate.now());
        task.setDeadline(LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> service.save(task))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Deadline cannot be earlier than the planned date");
    }

    @Test
    void testChangeStatusToCompletedSetsTimestamp() {
        Task task = new Task();
        task.setId(1L);
        task.setName("Task");
        task.setStatus(TaskStatus.IN_PROGRESS);
        when(repository.findById(1L)).thenReturn(Optional.of(task));

        service.changeStatus(1L, TaskStatus.COMPLETED, "Done successfully");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.getCompletedAt()).isNotNull();
        assertThat(task.getCompletionNote()).isEqualTo("Done successfully");
    }

    @Test
    void testArchiveAndSearch() {
        Task task = new Task();
        task.setId(1L);
        task.setArchived(false);
        when(repository.findById(1L)).thenReturn(Optional.of(task));

        service.archive(1L);
        assertThat(task.isArchived()).isTrue();

        when(repository.search("test", 1L, TaskStatus.IN_PROGRESS, Priority.HIGH)).thenReturn(List.of(task));
        List<Task> results = service.search("test", 1L, TaskStatus.IN_PROGRESS, Priority.HIGH);
        assertThat(results).hasSize(1);
    }

    @Test
    void testStartReworkAdvancesIterationAndResetsCompletion() {
        Task task = new Task();
        task.setId(10L);
        task.setName("Deploy update");
        task.setDescription("Shared description");
        task.setPlannedDate(LocalDate.now().minusDays(2));
        task.setDeadline(LocalDate.now().minusDays(1));
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(java.time.LocalDateTime.now().minusDays(1));
        task.setCompletionNote("First completion");
        task.setIteration(1);

        when(repository.findById(10L)).thenReturn(Optional.of(task));
        when(repository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDate newPlanned = LocalDate.now();
        LocalDate newDeadline = LocalDate.now().plusDays(3);
        Task reworked = service.startRework(10L, newPlanned, newDeadline);

        assertThat(reworked.getIteration()).isEqualTo(2);
        assertThat(reworked.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(reworked.getCompletedAt()).isNull();
        assertThat(reworked.getCompletionNote()).isNull();
        assertThat(reworked.getDescription()).isEqualTo("Shared description");
        assertThat(reworked.getPlannedDate()).isEqualTo(newPlanned);
        assertThat(reworked.getDeadline()).isEqualTo(newDeadline);
        verify(taskIterations, atLeastOnce()).save(any());
    }

    @Test
    void testStartReworkWithNoDeadlineClearsDeadline() {
        Task task = new Task();
        task.setId(15L);
        task.setName("Old task");
        task.setPlannedDate(LocalDate.now().minusDays(3));
        task.setDeadline(LocalDate.now().minusDays(1));
        task.setStatus(TaskStatus.COMPLETED);
        task.setIteration(1);

        when(repository.findById(15L)).thenReturn(Optional.of(task));
        when(repository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Task reworked = service.startRework(15L, LocalDate.now(), null);

        assertThat(reworked.getIteration()).isEqualTo(2);
        assertThat(reworked.getDeadline()).isNull();
    }

    @Test
    void testDeleteTask() {
        Task task = new Task();
        task.setId(5L);
        when(repository.findById(5L)).thenReturn(Optional.of(task));

        service.delete(5L);
        verify(repository).delete(task);
        verify(timeLogs).deleteByTaskId(5L);
    }

    @Test
    void testStartTimerSetsRunningForInProgressTask() {
        Task task = new Task();
        task.setId(10L);
        task.setStatus(TaskStatus.IN_PROGRESS);
        when(repository.findById(10L)).thenReturn(Optional.of(task));
        when(repository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Task updated = service.startTimer(10L);
        assertThat(updated.getTimerStatus()).isEqualTo(TaskTimerStatus.RUNNING);
        assertThat(updated.getTimerStartedAt()).isNotNull();
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void testMultipleTasksCanRunSimultaneously() {
        Task task1 = new Task();
        task1.setId(1L);
        task1.setName("Task 1");
        task1.setStatus(TaskStatus.IN_PROGRESS);

        Task task2 = new Task();
        task2.setId(2L);
        task2.setName("Task 2");
        task2.setStatus(TaskStatus.IN_PROGRESS);

        when(repository.findById(1L)).thenReturn(Optional.of(task1));
        when(repository.findById(2L)).thenReturn(Optional.of(task2));
        when(repository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.startTimer(1L);
        service.startTimer(2L);

        assertThat(task1.getTimerStatus()).isEqualTo(TaskTimerStatus.RUNNING);
        assertThat(task2.getTimerStatus()).isEqualTo(TaskTimerStatus.RUNNING);

        when(repository.findByTimerStatus(TaskTimerStatus.RUNNING)).thenReturn(List.of(task1, task2));
        List<Task> active = service.getActiveTimers();
        assertThat(active).hasSize(2).contains(task1, task2);
    }

    @Test
    void testPauseTimerRecordsAccumulatedTime() {
        Task task = new Task();
        task.setId(10L);
        task.setTimerStatus(TaskTimerStatus.RUNNING);
        task.setTimerStartedAt(java.time.LocalDateTime.now().minusSeconds(30));
        task.setTimerAccumulatedSeconds(15L);
        when(repository.findById(10L)).thenReturn(Optional.of(task));
        when(repository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Task paused = service.pauseTimer(10L);
        assertThat(paused.getTimerStatus()).isEqualTo(TaskTimerStatus.PAUSED);
        assertThat(paused.getTimerStartedAt()).isNull();
        assertThat(paused.getTimerAccumulatedSeconds()).isGreaterThanOrEqualTo(45L);
    }

    @Test
    void testStopTimerLogsSessionAndResets() {
        Task task = new Task();
        task.setId(10L);
        task.setTimerStatus(TaskTimerStatus.PAUSED);
        task.setTimerAccumulatedSeconds(60L);
        task.setTotalTimeSpentSeconds(120L);
        when(repository.findById(10L)).thenReturn(Optional.of(task));
        when(repository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Task stopped = service.stopTimer(10L, "Sprint work");
        assertThat(stopped.getTimerStatus()).isEqualTo(TaskTimerStatus.STOPPED);
        assertThat(stopped.getTimerStartedAt()).isNull();
        assertThat(stopped.getTimerAccumulatedSeconds()).isEqualTo(0L);
        assertThat(stopped.getTotalTimeSpentSeconds()).isEqualTo(180L);
        verify(timeLogs).save(any(TaskTimeLog.class));
    }

    @Test
    void testChangeStatusToCompletedAutoStopsTimer() {
        Task task = new Task();
        task.setId(10L);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setTimerStatus(TaskTimerStatus.RUNNING);
        task.setTimerStartedAt(java.time.LocalDateTime.now().minusSeconds(10));
        task.setTimerAccumulatedSeconds(50L);
        when(repository.findById(10L)).thenReturn(Optional.of(task));
        when(repository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.changeStatus(10L, TaskStatus.COMPLETED, "Done");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.getTimerStatus()).isEqualTo(TaskTimerStatus.STOPPED);
        assertThat(task.getTotalTimeSpentSeconds()).isGreaterThanOrEqualTo(60L);
    }
}
