package com.productivity.board.service;

import com.productivity.attachment.repository.AttachmentRepository;
import com.productivity.progress.repository.ProgressUpdateRepository;
import com.productivity.task.entity.Task;
import com.productivity.task.enums.TaskStatus;
import com.productivity.task.service.TaskService;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BoardService {
    private final TaskService tasks;
    private final AttachmentRepository attachmentRepository;
    private final ProgressUpdateRepository progressUpdateRepository;

    public BoardService(TaskService tasks) {
        this(tasks, null, null);
    }

    @Autowired
    public BoardService(TaskService tasks,
                        @Autowired(required = false) AttachmentRepository attachmentRepository,
                        @Autowired(required = false) ProgressUpdateRepository progressUpdateRepository) {
        this.tasks = tasks;
        this.attachmentRepository = attachmentRepository;
        this.progressUpdateRepository = progressUpdateRepository;
    }

    public Map<TaskStatus, List<Task>> board(LocalDate date, Long categoryId) {
        LocalDate selected = date == null ? LocalDate.now() : date;
        List<Task> dayTasks = tasks.forBoard(selected).stream()
                .filter(task -> categoryId == null || (task.getCategory() != null && task.getCategory().getId().equals(categoryId)))
                .toList();

        for (Task task : dayTasks) {
            if (attachmentRepository != null) {
                task.setAttachmentCount((int) attachmentRepository.countByTaskId(task.getId()));
            }
            if (progressUpdateRepository != null) {
                task.setProgressUpdateCount((int) progressUpdateRepository.countByTaskId(task.getId()));
            }
        }

        Map<TaskStatus, List<Task>> board = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            if (status.getBoardColumn() != status) continue;
            board.put(status, dayTasks.stream().filter(task -> task.getStatus().getBoardColumn() == status).toList());
        }
        return board;
    }
}
