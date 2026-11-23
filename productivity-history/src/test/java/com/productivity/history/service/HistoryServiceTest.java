package com.productivity.history.service;

import com.productivity.attachment.entity.Attachment;
import com.productivity.attachment.entity.ReferenceLink;
import com.productivity.attachment.service.AttachmentService;
import com.productivity.history.dto.request.HistorySearchRequest;
import com.productivity.history.dto.response.CompletedTaskResponse;
import com.productivity.history.dto.response.TaskHistoryDetailResponse;
import com.productivity.progress.entity.ProgressUpdate;
import com.productivity.progress.service.ProgressService;
import com.productivity.task.entity.Task;
import com.productivity.task.entity.TaskIteration;
import com.productivity.task.enums.Priority;
import com.productivity.task.service.TaskService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {

    @Mock
    private TaskService taskService;

    @Mock
    private ProgressService progressService;

    @Mock
    private AttachmentService attachmentService;

    private HistoryService service;

    @BeforeEach
    void setUp() {
        service = new HistoryService(taskService, progressService, attachmentService);
    }

    @Test
    void testSearchSuccess() {
        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now();

        Task task = new Task();
        task.setId(1L);
        task.setName("Completed task");

        when(taskService.completed("task", 1L, from, to)).thenReturn(List.of(task));

        List<Task> result = service.search("task", 1L, from, to);
        assertThat(result).containsExactly(task);
    }

    @Test
    void testSearchEndDateBeforeStartDateThrows() {
        LocalDate from = LocalDate.now();
        LocalDate to = LocalDate.now().minusDays(1);

        assertThatThrownBy(() -> service.search(null, null, from, to))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Completion end date cannot be before the start date");
    }

    @Test
    void testSearchWithRequestDto() {
        LocalDate from = LocalDate.now().minusDays(3);
        LocalDate to = LocalDate.now();
        HistorySearchRequest request = new HistorySearchRequest("doc", 2L, from, to);

        Task task = new Task();
        task.setId(10L);
        task.setName("Write documentation");
        task.setPriority(Priority.HIGH);
        task.setCompletedAt(LocalDateTime.now());

        when(taskService.completed("doc", 2L, from, to)).thenReturn(List.of(task));

        List<CompletedTaskResponse> results = service.search(request);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("Write documentation");
        assertThat(results.get(0).priority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void testGetTaskHistory() {
        Long taskId = 42L;
        Task task = new Task();
        task.setId(taskId);
        task.setName("Refactor modules");

        ProgressUpdate update = new ProgressUpdate();
        update.setId(101L);
        update.setDescription("Module split complete");

        Attachment attachment = new Attachment();
        attachment.setId(201L);
        attachment.setOriginalName("diagram.png");

        ReferenceLink link = new ReferenceLink();
        link.setId(301L);
        link.setUrl("https://antigravity.google.com");

        TaskIteration iteration = new TaskIteration();
        iteration.setIterationNumber(1);
        iteration.setCompletionNote("First iteration finished");

        when(taskService.get(taskId)).thenReturn(task);
        when(progressService.forTask(taskId)).thenReturn(List.of(update));
        when(attachmentService.allFilesForTask(taskId)).thenReturn(List.of(attachment));
        when(attachmentService.linksForTask(taskId)).thenReturn(List.of(link));
        when(taskService.iterationsForTask(taskId)).thenReturn(List.of(iteration));

        TaskHistoryDetailResponse historyDetail = service.getTaskHistory(taskId);
        assertThat(historyDetail.task().name()).isEqualTo("Refactor modules");
        assertThat(historyDetail.progressUpdates()).hasSize(1);
        assertThat(historyDetail.attachments()).hasSize(1);
        assertThat(historyDetail.referenceLinks()).hasSize(1);
        assertThat(historyDetail.iterations()).hasSize(1);
    }
}
