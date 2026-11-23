package com.productivity.progress.service;

import com.productivity.core.exception.ResourceNotFoundException;
import com.productivity.progress.entity.ProgressUpdate;
import com.productivity.progress.repository.ProgressUpdateRepository;
import com.productivity.task.entity.Task;
import com.productivity.task.service.TaskService;
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
class ProgressServiceTest {

    @Mock
    private ProgressUpdateRepository repository;

    @Mock
    private TaskService taskService;

    private ProgressService service;

    @BeforeEach
    void setUp() {
        service = new ProgressService(repository, taskService);
    }

    @Test
    void testForTask() {
        ProgressUpdate update = new ProgressUpdate();
        when(repository.findByTaskIdOrderByRecordedAtDesc(1L)).thenReturn(List.of(update));

        assertThat(service.forTask(1L)).hasSize(1);
    }

    @Test
    void testGetFoundAndNotFound() {
        ProgressUpdate update = new ProgressUpdate();
        update.setId(10L);
        when(repository.findById(10L)).thenReturn(Optional.of(update));
        when(repository.findById(20L)).thenReturn(Optional.empty());

        assertThat(service.get(10L).getId()).isEqualTo(10L);
        assertThatThrownBy(() -> service.get(20L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void testAddProgressUpdate() {
        Task task = new Task();
        task.setId(1L);
        when(taskService.get(1L)).thenReturn(task);

        ProgressUpdate update = new ProgressUpdate();
        update.setDescription("Finished phase 1");
        when(repository.save(any(ProgressUpdate.class))).thenAnswer(i -> i.getArgument(0));

        ProgressUpdate saved = service.add(1L, update);
        assertThat(saved.getTask()).isEqualTo(task);
        assertThat(saved.getDescription()).isEqualTo("Finished phase 1");
    }

    @Test
    void testAddProgressUpdateInheritsTaskIteration() {
        Task task = new Task();
        task.setId(1L);
        task.setIteration(3);
        when(taskService.get(1L)).thenReturn(task);

        ProgressUpdate update = new ProgressUpdate();
        update.setDescription("Finished phase 1");
        when(repository.save(any(ProgressUpdate.class))).thenAnswer(i -> i.getArgument(0));

        ProgressUpdate saved = service.add(1L, update);
        assertThat(saved.getTask()).isEqualTo(task);
        assertThat(saved.getDescription()).isEqualTo("Finished phase 1");
        assertThat(saved.getIteration()).isEqualTo(3);
    }

    @Test
    void testForTaskAndIteration() {
        ProgressUpdate update = new ProgressUpdate();
        update.setIteration(2);
        when(repository.findByTaskIdAndIterationOrderByRecordedAtDesc(1L, 2)).thenReturn(List.of(update));

        List<ProgressUpdate> list = service.forTaskAndIteration(1L, 2);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getIteration()).isEqualTo(2);
    }

    @Test
    void testRequireBelongsToTask() {
        Task task = new Task();
        task.setId(1L);
        ProgressUpdate update = new ProgressUpdate();
        update.setId(10L);
        update.setTask(task);
        when(repository.findById(10L)).thenReturn(Optional.of(update));

        service.requireBelongsToTask(10L, 1L);

        assertThatThrownBy(() -> service.requireBelongsToTask(10L, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to this task");
    }

    @Test
    void testDelete() {
        ProgressUpdate update = new ProgressUpdate();
        update.setId(5L);
        when(repository.findById(5L)).thenReturn(Optional.of(update));

        service.delete(5L);
        verify(repository).delete(update);
    }

    @Test
    void testDeleteWithTaskId() {
        Task task = new Task();
        task.setId(1L);
        ProgressUpdate update = new ProgressUpdate();
        update.setId(5L);
        update.setTask(task);
        when(repository.findById(5L)).thenReturn(Optional.of(update));

        service.delete(1L, 5L);
        verify(repository).delete(update);
    }

    @Test
    void testEditWithRecordedAt() {
        Task task = new Task();
        task.setId(1L);
        ProgressUpdate update = new ProgressUpdate();
        update.setId(5L);
        update.setTask(task);
        when(repository.findById(5L)).thenReturn(Optional.of(update));

        java.time.LocalDateTime customTime = java.time.LocalDateTime.of(2026, 11, 20, 10, 30);
        service.edit(1L, 5L, "Updated description", "Updated reminder", customTime);

        assertThat(update.getDescription()).isEqualTo("Updated description");
        assertThat(update.getReminder()).isEqualTo("Updated reminder");
        assertThat(update.getRecordedAt()).isEqualTo(customTime);
        verify(repository).save(update);
    }
}
