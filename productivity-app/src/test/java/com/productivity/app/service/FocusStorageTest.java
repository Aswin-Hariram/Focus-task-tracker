package com.productivity.app.service;

import com.productivity.attachment.repository.ReferenceLinkRepository;
import com.productivity.attachment.service.AttachmentService;
import com.productivity.progress.repository.ProgressUpdateRepository;
import com.productivity.task.repository.TaskActivityRepository;
import com.productivity.task.repository.TaskRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FocusStorageTest {
    @Test
    void createsDefaultFolderOnFirstSyncAndRemembersCustomSelection(@TempDir Path directory) throws Exception {
        Path documents = directory.resolve("Documents");
        Path state = directory.resolve("storage");
        var tasks = mock(TaskRepository.class);
        var updates = mock(ProgressUpdateRepository.class);
        var activity = mock(TaskActivityRepository.class);
        var attachments = mock(AttachmentService.class);
        var links = mock(ReferenceLinkRepository.class);
        var storage = new FocusStorage(state.toString(), documents.toString(), tasks, updates, activity, attachments, links);

        assertThat(storage.location()).isEqualTo(documents);
        storage.syncAll();
        assertThat(documents.resolve("focus")).isDirectory();

        Path custom = Files.createDirectory(directory.resolve("Custom"));
        storage.select(custom);
        var reopened = new FocusStorage(state.toString(), documents.toString(), tasks, updates, activity, attachments, links);
        assertThat(reopened.location()).isEqualTo(custom.toRealPath());
        assertThat(custom.resolve("focus")).isDirectory();
    }

    @Test
    void syncsTaskWithProgressFolderAttachmentsLinksAndCompleteMessageHistory(@TempDir Path directory) throws Exception {
        Path documents = directory.resolve("Documents");
        Path state = directory.resolve("storage");
        var tasks = mock(TaskRepository.class);
        var updates = mock(ProgressUpdateRepository.class);
        var activity = mock(TaskActivityRepository.class);
        var attachments = mock(AttachmentService.class);
        var links = mock(ReferenceLinkRepository.class);

        var task = new com.productivity.task.entity.Task();
        task.setId(42L);
        task.setName("Design Architecture");
        task.setDescription("Initial system design draft");
        task.restoreAuditTimestamps(java.time.LocalDateTime.of(2026, 11, 23, 10, 0, 0), null);
        task.setCompletedAt(java.time.LocalDateTime.of(2026, 11, 23, 18, 0, 0));
        task.setCompletionNote("All deliverables met and reviewed.");

        var update = new com.productivity.progress.entity.ProgressUpdate();
        update.setId(101L);
        update.setTask(task);
        update.setDescription("Backend module structure finalized");
        update.setRecordedAt(java.time.LocalDateTime.of(2026, 11, 23, 14, 30, 0));
        update.setReminder("Check database pooling configuration");

        var link = new com.productivity.attachment.entity.ReferenceLink();
        link.setId(201L);
        link.setTaskId(42L);
        link.setLabel("Architecture Diagram");
        link.setUrl("https://wiki.example.com/arch");
        link.setProgressUpdateId(101L);

        org.mockito.Mockito.when(tasks.findById(42L)).thenReturn(java.util.Optional.of(task));
        org.mockito.Mockito.when(tasks.findAll()).thenReturn(java.util.List.of(task));
        org.mockito.Mockito.when(updates.findByTaskIdOrderByRecordedAtDesc(42L)).thenReturn(java.util.List.of(update));
        org.mockito.Mockito.when(links.findByTaskIdOrderByCreatedAtDesc(42L)).thenReturn(java.util.List.of(link));
        org.mockito.Mockito.when(attachments.allFilesForTask(42L)).thenReturn(java.util.List.of());
        org.mockito.Mockito.when(activity.findByTaskIdOrderByOccurredAtDescIdDesc(42L)).thenReturn(java.util.List.of());

        var storage = new FocusStorage(state.toString(), documents.toString(), tasks, updates, activity, attachments, links);
        storage.sync(42L);

        Path focusFolder = documents.resolve("focus");
        assertThat(focusFolder).isDirectory();

        Path taskFolder = focusFolder.resolve("Design Architecture [42]");
        assertThat(taskFolder).isDirectory();

        // Check Progress update folder with timestamp
        Path updateFolder = taskFolder.resolve("Progress update - 2026-11-23_14-30-00 [101]");
        assertThat(updateFolder).isDirectory();

        // Check links.txt inside progress update folder
        Path linkFile = updateFolder.resolve("links.txt");
        assertThat(linkFile).isRegularFile();
        assertThat(Files.readString(linkFile)).contains("Architecture Diagram", "https://wiki.example.com/arch");

        // Check message.txt inside progress update folder containing progress update and message log from creation to end
        Path messageFile = updateFolder.resolve("message.txt");
        assertThat(messageFile).isRegularFile();
        String messageContent = Files.readString(messageFile);
        assertThat(messageContent).contains("Backend module structure finalized", "Task created", "Initial system design draft", "Task completed", "All deliverables met and reviewed.");

        // Check timeline.txt inside progress update folder
        Path timelineFile = updateFolder.resolve("timeline.txt");
        assertThat(timelineFile).isRegularFile();
        assertThat(Files.readString(timelineFile)).contains("Task created", "Task completed");
    }
}
