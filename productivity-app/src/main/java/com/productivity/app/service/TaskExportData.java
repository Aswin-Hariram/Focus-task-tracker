package com.productivity.app.service;

import com.productivity.attachment.entity.Attachment;
import com.productivity.attachment.entity.ReferenceLink;
import com.productivity.attachment.repository.AttachmentRepository;
import com.productivity.attachment.repository.ReferenceLinkRepository;
import com.productivity.progress.entity.ProgressUpdate;
import com.productivity.progress.service.ProgressService;
import com.productivity.task.entity.Task;
import com.productivity.task.entity.TaskActivity;
import com.productivity.task.entity.TaskIteration;
import com.productivity.task.entity.TaskTimeLog;
import com.productivity.task.repository.TaskActivityRepository;
import com.productivity.task.repository.TaskRepository;
import com.productivity.task.service.TaskService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class TaskExportData {
    private final TaskService tasks;
    private final TaskRepository taskRepository;
    private final ProgressService progress;
    private final TaskActivityRepository activity;
    private final AttachmentRepository attachments;
    private final ReferenceLinkRepository links;

    TaskExportData(TaskService tasks, TaskRepository taskRepository, ProgressService progress,
                   TaskActivityRepository activity, AttachmentRepository attachments, ReferenceLinkRepository links) {
        this.tasks = tasks;
        this.taskRepository = taskRepository;
        this.progress = progress;
        this.activity = activity;
        this.attachments = attachments;
        this.links = links;
    }

    public List<Task> task(Long id) {
        return List.of(tasks.get(id));
    }

    public List<Task> allTasks() {
        return taskRepository.findAll(Sort.by("id"));
    }

    public List<Task> completed(String query, Long category, LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("Completion end date cannot be before the start date");
        }
        return tasks.completed(query, category, from, to);
    }

    public Details details(Long id) {
        return new Details(progress.forTask(id), activity.findByTaskIdOrderByOccurredAtDescIdDesc(id),
                tasks.iterationsForTask(id), tasks.timeLogsForTask(id), links.findByTaskIdOrderByCreatedAtDesc(id),
                attachments.findByTaskIdOrderByCreatedAtDesc(id));
    }

    record Details(List<ProgressUpdate> progress, List<TaskActivity> events, List<TaskIteration> iterations,
                   List<TaskTimeLog> timeLogs, List<ReferenceLink> links, List<Attachment> attachments) {}
}
