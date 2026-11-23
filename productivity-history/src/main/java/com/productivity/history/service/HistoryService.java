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
import com.productivity.task.service.TaskService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HistoryService {
    private final TaskService tasks;
    private final ProgressService progress;
    private final AttachmentService attachments;

    public HistoryService(TaskService tasks, ProgressService progress, AttachmentService attachments) {
        this.tasks = tasks;
        this.progress = progress;
        this.attachments = attachments;
    }

    public List<Task> search(String query, Long categoryId, LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("Completion end date cannot be before the start date");
        }
        return tasks.completed(query, categoryId, from, to);
    }

    public List<CompletedTaskResponse> search(HistorySearchRequest request) {
        if (request == null) {
            return search(null, null, null, null).stream().map(CompletedTaskResponse::from).toList();
        }
        return search(request.query(), request.categoryId(), request.from(), request.to())
                .stream()
                .map(CompletedTaskResponse::from)
                .toList();
    }

    public TaskHistoryDetailResponse getTaskHistory(Long taskId) {
        Task task = tasks.get(taskId);
        List<ProgressUpdate> updates = progress.forTask(taskId);
        List<Attachment> taskAttachments = attachments.allFilesForTask(taskId);
        List<ReferenceLink> links = attachments.linksForTask(taskId);
        List<TaskIteration> iterations = tasks.iterationsForTask(taskId);
        return new TaskHistoryDetailResponse(
                CompletedTaskResponse.from(task),
                updates,
                taskAttachments,
                links,
                iterations
        );
    }
}
