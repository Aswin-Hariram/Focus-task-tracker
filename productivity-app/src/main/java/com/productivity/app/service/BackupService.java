package com.productivity.app.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productivity.attachment.entity.Attachment;
import com.productivity.attachment.entity.ReferenceLink;
import com.productivity.attachment.repository.AttachmentRepository;
import com.productivity.attachment.repository.ReferenceLinkRepository;
import com.productivity.category.entity.Category;
import com.productivity.category.repository.CategoryRepository;
import com.productivity.core.exception.ResourceNotFoundException;
import com.productivity.core.security.SecurityUtils;
import com.productivity.core.security.XmlSecurityConfig;
import com.productivity.progress.entity.ProgressUpdate;
import com.productivity.progress.repository.ProgressUpdateRepository;
import com.productivity.task.entity.Task;
import com.productivity.task.entity.TaskActivity;
import com.productivity.task.entity.TaskIteration;
import com.productivity.task.entity.TaskTimeLog;
import com.productivity.task.enums.Priority;
import com.productivity.task.enums.TaskStatus;
import com.productivity.task.enums.TaskTimerStatus;
import com.productivity.task.repository.TaskRepository;
import com.productivity.task.repository.TaskActivityRepository;
import com.productivity.task.repository.TaskTimeLogRepository;
import com.productivity.task.repository.TaskIterationRepository;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.CheckedInputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BackupService {
    private static final Logger LOG = LoggerFactory.getLogger(BackupService.class);
    private static final String MANIFEST = "focus-backup.json";

    private final CategoryRepository categories;
    private final TaskRepository tasks;
    private final TaskActivityRepository activity;
    private final ProgressUpdateRepository updates;
    private final AttachmentRepository attachments;
    private final ReferenceLinkRepository links;
    private final TaskTimeLogRepository timeLogs;
    private final ObjectMapper objectMapper;
    private final Path attachmentRoot;
    private final Path progressRoot;
    private final JdbcTemplate jdbc;
    private final TaskIterationRepository iterations;

    public BackupService(CategoryRepository categories, TaskRepository tasks, TaskActivityRepository activity,
                         ProgressUpdateRepository updates, AttachmentRepository attachments,
                         ReferenceLinkRepository links, TaskTimeLogRepository timeLogs,
                         TaskIterationRepository iterations, ObjectMapper objectMapper, JdbcTemplate jdbc,
                         @Value("${app.storage.root:./storage}") String storageRoot) {
        this.categories = categories;
        this.tasks = tasks;
        this.activity = activity;
        this.updates = updates;
        this.attachments = attachments;
        this.links = links;
        this.timeLogs = timeLogs;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
        this.iterations = iterations;
        Path base = Path.of(storageRoot).toAbsolutePath().normalize();
        this.attachmentRoot = base.resolve("task-attachments");
        this.progressRoot = base.resolve("progress-attachments");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void createBackup(OutputStream output) throws IOException {
        List<Attachment> allAttachments = attachments.findAll();
        BackupData data = new BackupData(
                2,
                Instant.now(),
                categories.findAll().stream().map(this::categoryData).toList(),
                tasks.findAll().stream().map(this::taskData).toList(),
                updates.findAll().stream().map(this::progressData).toList(),
                allAttachments.stream().map(this::attachmentData).toList(),
                links.findAll().stream().map(this::linkData).toList(),
                activity.findAllByOrderByIdAsc().stream().map(ActivityData::from).toList(),
                iterations.findAll().stream().map(IterationData::from).toList(),
                timeLogs.findAll().stream().map(TimeLogData::from).toList()
        );

        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(MANIFEST));
            objectMapper.writer().without(JsonGenerator.Feature.AUTO_CLOSE_TARGET).writeValue(zip, data);
            zip.closeEntry();
            for (Attachment attachment : allAttachments) {
                Path file = safeAttachmentPath(attachment.getStoredName());
                if (!Files.isRegularFile(file)) throw new IOException("Missing attachment file: " + attachment.getOriginalName());
                zip.putNextEntry(new ZipEntry(entryName(attachment.getId())));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public RestoreResult restore(MultipartFile backup) throws IOException {
        if (backup == null || backup.isEmpty()) throw new IllegalArgumentException("Choose a Focus backup archive");
        Path archive = Files.createTempFile("focus-restore-", ".zip");
        try {
            backup.transferTo(archive);
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                Map<String, ZipEntry> entries = readEntries(zip);
                ZipEntry manifest = entries.get(MANIFEST);
                if (manifest == null) throw new IllegalArgumentException("This is not a valid Focus backup archive");
                BackupData data;
                try (var input = zip.getInputStream(manifest)) {
                    data = objectMapper.readValue(input, BackupData.class);
                }
                if (data.version() != 1 && data.version() != 2) throw new IllegalArgumentException("Unsupported backup version: " + data.version());
                validateReferences(data, entries);
                return restoreData(data, zip, entries);
            }
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    private RestoreResult restoreData(BackupData data, ZipFile zip, Map<String, ZipEntry> entries) throws IOException {
        lockWorkspace();
        List<Path> oldFiles = attachments.findAll().stream()
                .map(item -> safeAttachmentPath(item.getStoredName())).toList();
        List<Path> createdFiles = new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                List<Path> obsolete = status == STATUS_COMMITTED ? oldFiles : createdFiles;
                for (Path file : obsolete) {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException ex) {
                        LOG.warn("Could not remove obsolete attachment after backup restore", ex);
                    }
                }
            }
        });
        try {
            clearWorkspaceRecords();

            Map<Long, Category> categoryMap = new HashMap<>();
            for (CategoryData item : data.categories()) {
                categoryMap.put(item.id(), restoreCategory(item));
            }

            Map<Long, Task> taskMap = new HashMap<>();
            for (TaskData item : data.tasks()) {
                taskMap.put(item.id(), restoreTask(item, required(categoryMap, item.categoryId(), "category")));
            }

            Map<Long, ProgressUpdate> updateMap = restoreProgress(data.progressUpdates(), taskMap);
            restoreTaskHistory(data.iterations(), data.timeLogs(), taskMap);
            if (data.activity() != null) {
                // Keep saved activity instead of the creation events generated by the import.
                activity.deleteAllInBatch();
                restoreActivity(data.activity(), taskMap, categoryMap, updateMap);
            }
            restoreLinks(data.links(), taskMap, updateMap);

            Files.createDirectories(attachmentRoot);
            Files.createDirectories(progressRoot);
            for (AttachmentData item : data.attachments()) {
                String storedName = UUID.randomUUID() + extension(item.originalName());
                Path target = targetAttachmentPath(storedName, item.progressUpdateId() != null);
                createdFiles.add(target);
                copyAttachment(zip, entries.get(entryName(item.id())), target);
                restoreAttachment(item, storedName, taskMap, updateMap);
            }
            attachments.flush();
        } catch (Exception ex) {
            if (ex instanceof IOException io) throw io;
            if (ex instanceof IllegalArgumentException illegal) throw illegal;
            throw new IllegalStateException("Backup restore failed", ex);
        }

        return new RestoreResult(data.categories().size(), data.tasks().size(), data.progressUpdates().size(), data.attachments().size());
    }

    @Transactional(rollbackFor = IOException.class)
    public String deleteTask(Long id) throws IOException {
        lockWorkspace();
        jdbc.queryForList("select id from tasks where id = ? for update", Long.class, id);
        Task task = tasks.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Task not found"));
        var taskAttachments = attachments.findByTaskIdOrderByCreatedAtDesc(id);
        var taskLinks = links.findByTaskIdOrderByCreatedAtDesc(id);
        var taskUpdates = updates.findByTaskIdOrderByRecordedAtDesc(id);
        var taskIterations = iterations.findByTaskIdOrderByIterationNumberAsc(id);
        var taskTimeLogs = timeLogs.findByTaskIdOrderByStartedAtDesc(id);
        DeletedTask deleted = new DeletedTask(taskData(task), categoryData(task.getCategory()),
                taskUpdates.stream().map(this::progressData).toList(),
                taskAttachments.stream().map(item -> new RetainedAttachment(attachmentData(item), item.getStoredName())).toList(),
                taskLinks.stream().map(this::linkData).toList(),
                activity.findByTaskIdOrderByOccurredAtDescIdDesc(id).stream()
                        .sorted(Comparator.comparing(TaskActivity::getOccurredAt).thenComparing(TaskActivity::getId))
                        .map(ActivityData::from).toList(),
                taskIterations.stream().map(IterationData::from).toList(),
                taskTimeLogs.stream().map(TimeLogData::from).toList());
        jdbc.update("insert into deleted_tasks (task_id, task_name, snapshot) values (?, ?, ?)", id, task.getName(),
                objectMapper.writeValueAsString(deleted));
        attachments.deleteAll(taskAttachments);
        links.deleteAll(taskLinks);
        updates.deleteAll(taskUpdates);
        iterations.deleteAll(taskIterations);
        timeLogs.deleteAll(taskTimeLogs);
        tasks.delete(task);
        tasks.flush();
        return task.getName();
    }

    @Transactional(rollbackFor = IOException.class)
    public Task undoTaskDeletion(Long id) throws IOException {
        lockWorkspace();
        List<String> snapshots = jdbc.queryForList("select snapshot from deleted_tasks where task_id = ?", String.class, id);
        if (snapshots.isEmpty()) return null;
        DeletedTask deleted = objectMapper.readValue(snapshots.get(0), DeletedTask.class);
        CategoryData original = deleted.category();
        Category category = categories.findById(original.id()).orElseGet(() ->
                categories.findByNameIgnoreCase(original.name()).orElseGet(() -> restoreCategory(original)));
        Task task = restoreTask(deleted.task(), category);
        Map<Long, Task> taskMap = Map.of(id, task);
        Map<Long, ProgressUpdate> updateMap = restoreProgress(deleted.progress(), taskMap);
        restoreTaskHistory(deleted.iterations(), deleted.timeLogs(), taskMap);
        restoreLinks(deleted.links(), taskMap, updateMap);
        for (RetainedAttachment retained : deleted.attachments()) {
            if (!Files.isRegularFile(safeAttachmentPath(retained.storedName()))) {
                throw new IOException("A deleted task attachment is missing.");
            }
            restoreAttachment(retained.data(), retained.storedName(), taskMap, updateMap);
        }

        jdbc.update("delete from task_activity where task_id in (?, ?)", id, task.getId());
        restoreActivity(deleted.activity(), taskMap, Map.of(original.id(), category), updateMap);
        jdbc.update("delete from deleted_tasks where task_id = ?", id);
        return task;
    }

    public List<DeletedTaskSummary> deletedTasks() {
        return jdbc.query("select task_id, task_name from deleted_tasks order by deleted_at desc, task_id desc",
                (row, index) -> new DeletedTaskSummary(row.getLong("task_id"), row.getString("task_name")));
    }

    private void lockWorkspace() {
        jdbc.queryForList("select id from workspace_settings where id = 1 for update", Integer.class);
    }

    private List<Path> recycledAttachmentPaths() throws IOException {
        List<Path> files = new ArrayList<>();
        for (String snapshot : jdbc.queryForList("select snapshot from deleted_tasks", String.class)) {
            DeletedTask deleted = objectMapper.readValue(snapshot, DeletedTask.class);
            for (RetainedAttachment retained : deleted.attachments()) {
                files.add(safeAttachmentPath(retained.storedName()));
            }
        }
        return files;
    }

    private Category restoreCategory(CategoryData item) {
        Category category = new Category();
        category.setName(item.name());
        category.setColor(item.color());
        category.setArchived(item.archived());
        category.restoreAuditTimestamps(item.createdAt(), item.updatedAt());
        return categories.save(category);
    }

    private Task restoreTask(TaskData item, Category category) {
        Task task = new Task();
        task.setName(item.name());
        task.setDescription(item.description());
        task.setPlannedDate(item.plannedDate());
        task.setDeadline(item.deadline());
        task.setStatus(item.status());
        task.setPriority(item.priority());
        task.setCategory(category);
        task.setCompletedAt(item.completedAt());
        task.setCompletionNote(item.completionNote());
        task.setArchived(item.archived());
        task.setIteration(item.iteration() != null ? item.iteration() : 1);
        task.setBoardPosition(item.boardPosition());
        task.setTotalTimeSpentSeconds(item.totalTimeSpentSeconds());
        task.setTimerStatus(item.timerStatus());
        task.setTimerStartedAt(item.timerStartedAt());
        task.setTimerSessionStartedAt(item.timerSessionStartedAt());
        task.setTimerAccumulatedSeconds(item.timerAccumulatedSeconds());
        task.setTimerDailySeconds(item.timerDailySeconds());
        task.restoreAuditTimestamps(item.createdAt(), item.updatedAt());
        return tasks.save(task);
    }

    private Map<Long, ProgressUpdate> restoreProgress(List<ProgressData> records, Map<Long, Task> taskMap) {
        Map<Long, ProgressUpdate> updateMap = new HashMap<>();
        for (ProgressData item : records) {
            ProgressUpdate update = new ProgressUpdate();
            update.setTask(required(taskMap, item.taskId(), "task"));
            update.setDescription(item.description());
            update.setReminder(item.reminder());
            update.setRecordedAt(item.recordedAt());
            update.setIteration(item.iteration() != null ? item.iteration() : 1);
            update.restoreAuditTimestamps(item.createdAt(), item.updatedAt());
            updateMap.put(item.id(), updates.save(update));
        }
        return updateMap;
    }

    private void restoreTaskHistory(List<IterationData> iterationData, List<TimeLogData> logData, Map<Long, Task> taskMap) {
        for (IterationData item : iterationData == null ? List.<IterationData>of() : iterationData) {
            TaskIteration iteration = new TaskIteration();
            iteration.setTask(required(taskMap, item.taskId(), "task"));
            iteration.setIterationNumber(item.number());
            iteration.setStartedAt(item.startedAt());
            iteration.setCompletedAt(item.completedAt());
            iteration.setCompletionNote(item.completionNote());
            iteration.restoreAuditTimestamps(item.createdAt(), item.updatedAt());
            iterations.save(iteration);
        }
        for (TimeLogData item : logData == null ? List.<TimeLogData>of() : logData) {
            TaskTimeLog log = new TaskTimeLog(required(taskMap, item.taskId(), "task"), item.startedAt(),
                    item.endedAt(), item.durationSeconds(), item.note());
            if (item.dailySeconds() != null) log.setDailySeconds(item.dailySeconds());
            log.restoreAuditTimestamps(item.createdAt(), item.updatedAt());
            timeLogs.save(log);
        }
    }

    private void restoreActivity(List<ActivityData> records, Map<Long, Task> taskMap, Map<Long, Category> categoryMap,
                                 Map<Long, ProgressUpdate> updateMap) {
        Map<Long, Long> historicalIds = new HashMap<>();
        for (ActivityData item : records) {
            Task task = taskMap.get(item.taskId());
            Long taskId = task == null
                    ? historicalIds.computeIfAbsent(item.taskId(), ignored -> -1L - historicalIds.size())
                    : task.getId();
            Category category = categoryMap.get(item.categoryId());
            Long categoryId = category == null ? 0L : category.getId();
            TaskActivity restored = new TaskActivity(taskId, item.taskName(), categoryId, item.categoryName(),
                    item.categoryColor(), item.action(), item.status(), item.previousStatus(),
                    item.occurredAt(), item.note());
            restored.setProgressUpdateId(restoreProgressId(updateMap, item.progressUpdateId(), taskId));
            activity.save(restored);
        }
    }

    private void restoreLinks(List<LinkData> records, Map<Long, Task> taskMap, Map<Long, ProgressUpdate> updateMap) {
        for (LinkData item : records) {
            ReferenceLink link = new ReferenceLink();
            Long taskId = required(taskMap, item.taskId(), "task").getId();
            link.setTaskId(taskId);
            link.setProgressUpdateId(restoreProgressId(updateMap, item.progressUpdateId(), taskId));
            link.setLabel(item.label());
            link.setUrl(item.url());
            link.restoreAuditTimestamps(item.createdAt(), item.updatedAt());
            links.save(link);
        }
    }

    private void restoreAttachment(AttachmentData item, String storedName, Map<Long, Task> taskMap,
                                   Map<Long, ProgressUpdate> updateMap) {
        Attachment attachment = new Attachment();
        Long taskId = required(taskMap, item.taskId(), "task").getId();
        attachment.setTaskId(taskId);
        attachment.setProgressUpdateId(restoreProgressId(updateMap, item.progressUpdateId(), taskId));
        attachment.setOriginalName(item.originalName());
        attachment.setStoredName(storedName);
        attachment.setContentType(item.contentType());
        attachment.setSizeBytes(item.sizeBytes());
        attachment.restoreAuditTimestamps(item.createdAt(), item.updatedAt());
        attachments.save(attachment);
    }

    private Long restoreProgressId(Map<Long, ProgressUpdate> updateMap, Long progressId, Long taskId) {
        if (progressId == null) return null;
        ProgressUpdate update = required(updateMap, progressId, "progress update");
        if (!update.getTask().getId().equals(taskId)) {
            throw new IllegalArgumentException("Progress update belongs to a different task");
        }
        return update.getId();
    }

    @Transactional(rollbackFor = IOException.class)
    public void resetWorkspace() throws IOException {
        lockWorkspace();
        Set<Path> files = new LinkedHashSet<>(recycledAttachmentPaths());
        for (Attachment item : attachments.findAll()) {
            files.add(SecurityUtils.validatePathWithinRoot(attachmentRoot.resolve(item.getStoredName()), attachmentRoot));
            files.add(SecurityUtils.validatePathWithinRoot(progressRoot.resolve(item.getStoredName()), progressRoot));
        }
        for (Path directory : List.of(attachmentRoot, progressRoot)) {
            if (!Files.isDirectory(directory, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
            try (var entries = Files.list(directory)) {
                entries.filter(path -> Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                        .filter(path -> path.getFileName().toString().matches(
                                "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}(?:[.].+)?"))
                        .forEach(files::add);
            }
        }

        clearWorkspaceRecords();
        jdbc.update("delete from deleted_tasks");
        jdbc.update("update workspace_settings set demo_seed_disabled = true where id = 1");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                IOException failure = null;
                for (Path file : files) {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException ex) {
                        if (failure == null) failure = ex;
                        else failure.addSuppressed(ex);
                    }
                }
                if (failure != null) throw new UncheckedIOException(failure);
            }
        });
    }

    private void clearWorkspaceRecords() {
        attachments.deleteAllInBatch();
        links.deleteAllInBatch();
        updates.deleteAllInBatch();
        timeLogs.deleteAllInBatch();
        iterations.deleteAllInBatch();
        tasks.deleteAllInBatch();
        activity.deleteAllInBatch();
        categories.deleteAllInBatch();
    }

    private Map<String, ZipEntry> readEntries(ZipFile zip) {
        Map<String, ZipEntry> entries = new LinkedHashMap<>();
        var archiveEntries = zip.entries();
        while (archiveEntries.hasMoreElements()) {
            ZipEntry entry = archiveEntries.nextElement();
            if (entry.isDirectory()) continue;
            String name = entry.getName();
            if (name.contains("..") || name.startsWith("/") || name.contains("\\") || entries.containsKey(name)) {
                throw new IllegalArgumentException("Backup contains an invalid entry");
            }
            if (!XmlSecurityConfig.getInstance().matchesRegex("backup-entry", name)) {
                throw new IllegalArgumentException("Backup contains an unexpected entry: " + name);
            }
            if (entries.size() >= 10_000) {
                throw new IllegalArgumentException("Backup archive exceeds entry count limits");
            }
            entries.put(name, entry);
        }
        return entries;
    }

    private void copyAttachment(ZipFile zip, ZipEntry entry, Path target) throws IOException {
        try (var input = new CheckedInputStream(zip.getInputStream(entry), new CRC32());
             var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[8192];
            long remaining = entry.getSize();
            int read;
            while ((read = input.read(buffer)) != -1) {
                remaining -= read;
                if (remaining < 0) throw new IOException("Attachment exceeds its declared size.");
                output.write(buffer, 0, read);
            }
            if (remaining != 0 || input.getChecksum().getValue() != entry.getCrc()) {
                throw new IOException("Backup contains a damaged attachment.");
            }
        }
    }

    private void validateReferences(BackupData data, Map<String, ZipEntry> entries) {
        Map<Long, CategoryData> categoryIds = index(data.categories());
        Map<Long, TaskData> taskIds = index(data.tasks());
        Map<Long, ProgressData> progressIds = index(data.progressUpdates());
        if (data.activity() != null) {
            for (ActivityData item : data.activity()) {
                if (item == null || item.taskId() == null || item.categoryId() == null
                        || item.action() == null || item.occurredAt() == null || item.categoryColor() == null
                        || !item.categoryColor().matches("#[0-9a-fA-F]{6}")) {
                    throw new IllegalArgumentException("Backup contains invalid task activity");
                }
            }
        }
        data.tasks().forEach(item -> required(categoryIds, item.categoryId(), "category"));
        data.progressUpdates().forEach(item -> required(taskIds, item.taskId(), "task"));
        data.links().forEach(item -> required(taskIds, item.taskId(), "task"));
        data.attachments().forEach(item -> {
            required(taskIds, item.taskId(), "task");
            if (item.progressUpdateId() != null) required(progressIds, item.progressUpdateId(), "progress update");
            ZipEntry entry = entries.get(entryName(item.id()));
            if (entry == null) throw new IllegalArgumentException("Backup is missing an attachment file");
            if (entry.getSize() < 0 || entry.getSize() != item.sizeBytes()) {
                throw new IllegalArgumentException("Backup attachment size does not match its manifest.");
            }
        });
    }

    private <T extends Identified> Map<Long, T> index(List<T> items) {
        Map<Long, T> result = new HashMap<>();
        for (T item : items) {
            if (item.id() == null || result.put(item.id(), item) != null) throw new IllegalArgumentException("Backup contains duplicate identifiers");
        }
        return result;
    }

    private <T> T required(Map<Long, T> map, Long id, String type) {
        T value = map.get(id);
        if (value == null) throw new IllegalArgumentException("Backup references a missing " + type);
        return value;
    }

    private Path safeAttachmentPath(String storedName) {
        Path taskPath = SecurityUtils.validatePathWithinRoot(attachmentRoot.resolve(storedName), attachmentRoot);
        Path progPath = SecurityUtils.validatePathWithinRoot(progressRoot.resolve(storedName), progressRoot);
        if (Files.isRegularFile(progPath)) return progPath;
        if (Files.isRegularFile(taskPath)) return taskPath;
        return taskPath;
    }

    private Path targetAttachmentPath(String storedName, boolean isProgress) {
        Path targetDir = isProgress ? progressRoot : attachmentRoot;
        return SecurityUtils.validatePathWithinRoot(targetDir.resolve(storedName), targetDir);
    }

    private String extension(String originalName) {
        int dot = originalName == null ? -1 : originalName.lastIndexOf('.');
        return dot < 0 ? "" : originalName.substring(dot).toLowerCase();
    }

    private String entryName(Long id) { return "attachments/" + id + ".bin"; }

    private CategoryData categoryData(Category value) { return new CategoryData(value.getId(), value.getName(), value.getColor(), value.isArchived(), value.getCreatedAt(), value.getUpdatedAt()); }
    private TaskData taskData(Task value) {
        return new TaskData(value.getId(), value.getCategory().getId(), value.getName(), value.getDescription(),
                value.getPlannedDate(), value.getDeadline(), value.getStatus(), value.getPriority(),
                value.getCompletedAt(), value.getCompletionNote(), value.getIteration(), value.isArchived(),
                value.getCreatedAt(), value.getUpdatedAt(), value.getBoardPosition(), value.getTotalTimeSpentSeconds(),
                value.getTimerStatus(), value.getTimerStartedAt(), value.getTimerSessionStartedAt(),
                value.getTimerAccumulatedSeconds(), value.getTimerDailySeconds());
    }
    private ProgressData progressData(ProgressUpdate value) { return new ProgressData(value.getId(), value.getTask().getId(), value.getDescription(), value.getReminder(), value.getRecordedAt(), value.getIteration(), value.getCreatedAt(), value.getUpdatedAt()); }
    private AttachmentData attachmentData(Attachment value) { return new AttachmentData(value.getId(), value.getTaskId(), value.getProgressUpdateId(), value.getOriginalName(), value.getContentType(), value.getSizeBytes(), value.getCreatedAt(), value.getUpdatedAt()); }
    private LinkData linkData(ReferenceLink value) { return new LinkData(value.getId(), value.getTaskId(), value.getLabel(), value.getUrl(), value.getCreatedAt(), value.getUpdatedAt(), value.getProgressUpdateId()); }

    public record RestoreResult(int categories, int tasks, int progressUpdates, int attachments) {}
    public interface Identified { Long id(); }
    public record BackupData(int version, Instant exportedAt, List<CategoryData> categories, List<TaskData> tasks,
                             List<ProgressData> progressUpdates, List<AttachmentData> attachments, List<LinkData> links,
                             List<ActivityData> activity, List<IterationData> iterations, List<TimeLogData> timeLogs) {}
    public record DeletedTaskSummary(Long id, String name) {}
    private record DeletedTask(TaskData task, CategoryData category, List<ProgressData> progress,
                               List<RetainedAttachment> attachments, List<LinkData> links, List<ActivityData> activity,
                               List<IterationData> iterations, List<TimeLogData> timeLogs) {}
    private record RetainedAttachment(AttachmentData data, String storedName) {}
    public record IterationData(Long taskId, int number, LocalDateTime startedAt, LocalDateTime completedAt,
                                String completionNote, LocalDateTime createdAt, LocalDateTime updatedAt) {
        static IterationData from(TaskIteration item) {
            return new IterationData(item.getTask().getId(), item.getIterationNumber(), item.getStartedAt(),
                    item.getCompletedAt(), item.getCompletionNote(), item.getCreatedAt(), item.getUpdatedAt());
        }
    }
    public record TimeLogData(Long taskId, LocalDateTime startedAt, LocalDateTime endedAt, long durationSeconds,
                              String note, Map<LocalDate, Long> dailySeconds,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
        static TimeLogData from(TaskTimeLog item) {
            return new TimeLogData(item.getTask().getId(), item.getStartedAt(), item.getEndedAt(), item.getDurationSeconds(),
                    item.getNote(), item.getDailySeconds(), item.getCreatedAt(), item.getUpdatedAt());
        }
    }
    public record ActivityData(Long taskId, String taskName, Long categoryId, String categoryName, String categoryColor,
                               TaskActivity.Action action, TaskStatus status, TaskStatus previousStatus,
                               LocalDateTime occurredAt, String note, Long progressUpdateId) {
        static ActivityData from(TaskActivity item) {
            return new ActivityData(item.getTaskId(), item.getTaskName(), item.getCategoryId(), item.getCategoryName(),
                    item.getCategoryColor(), item.getAction(), item.getStatus(), item.getPreviousStatus(),
                    item.getOccurredAt(), item.getNote(), item.getProgressUpdateId());
        }
    }
    public record CategoryData(Long id, String name, String color, boolean archived, LocalDateTime createdAt, LocalDateTime updatedAt) implements Identified {}
    public record TaskData(Long id, Long categoryId, String name, String description, LocalDate plannedDate, LocalDate deadline,
                           TaskStatus status, Priority priority, LocalDateTime completedAt, String completionNote, Integer iteration, boolean archived,
                           LocalDateTime createdAt, LocalDateTime updatedAt, Integer boardPosition, long totalTimeSpentSeconds,
                           TaskTimerStatus timerStatus, LocalDateTime timerStartedAt, LocalDateTime timerSessionStartedAt,
                           long timerAccumulatedSeconds, Map<LocalDate, Long> timerDailySeconds) implements Identified {}
    public record ProgressData(Long id, Long taskId, String description, String reminder, LocalDateTime recordedAt, Integer iteration,
                               LocalDateTime createdAt, LocalDateTime updatedAt) implements Identified {}
    public record AttachmentData(Long id, Long taskId, Long progressUpdateId, String originalName, String contentType,
                                 long sizeBytes, LocalDateTime createdAt, LocalDateTime updatedAt) implements Identified {}
    public record LinkData(Long id, Long taskId, String label, String url, LocalDateTime createdAt, LocalDateTime updatedAt, Long progressUpdateId) implements Identified {}
}
