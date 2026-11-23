package com.productivity.app.service;

import com.productivity.attachment.entity.ReferenceLink;
import com.productivity.attachment.repository.ReferenceLinkRepository;
import com.productivity.attachment.service.AttachmentService;
import com.productivity.progress.repository.ProgressUpdateRepository;
import com.productivity.task.entity.Task;
import com.productivity.task.entity.TaskActivity;
import com.productivity.task.repository.TaskActivityRepository;
import com.productivity.task.repository.TaskRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FocusStorage {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final Path state;
    private final Path locationFile;
    private final Path defaultLocation;
    private final TaskRepository tasks;
    private final ProgressUpdateRepository progress;
    private final TaskActivityRepository activity;
    private final AttachmentService attachments;
    private final ReferenceLinkRepository links;

    public FocusStorage(@Value("${app.storage.root:./storage}") String storageRoot,
                        @Value("${app.storage.default-location:${user.home}/Documents}") String defaultLocation,
                        TaskRepository tasks,
                        ProgressUpdateRepository progress, TaskActivityRepository activity,
                        AttachmentService attachments, ReferenceLinkRepository links) {
        this.state = Path.of(storageRoot).toAbsolutePath().normalize().resolve("focus-location");
        this.locationFile = state.resolve("location.txt");
        this.defaultLocation = defaultLocation.isBlank() ? null : Path.of(defaultLocation).toAbsolutePath().normalize();
        this.tasks = tasks;
        this.progress = progress;
        this.activity = activity;
        this.attachments = attachments;
        this.links = links;
    }

    public synchronized Path location() throws IOException {
        if (Files.notExists(locationFile)) return defaultLocation;
        return Path.of(Files.readString(locationFile));
    }

    public synchronized void select(Path directory) throws IOException {
        if (!directory.isAbsolute() || !Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Select an existing folder using its full path.");
        }

        Path selected = directory.toRealPath();
        Path root = selected.resolve("focus");
        boolean reconcileDeletions = Files.exists(locationFile) && selected.equals(location())
                && Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS);
        ensureDirectory(root);
        Path probe = Files.createTempFile(root, ".focus-write-", ".tmp");
        Files.delete(probe);
        syncAll(selected, reconcileDeletions);
        Files.createDirectories(state);
        replace(locationFile, selected.toString());
    }

    public synchronized void syncAll() throws IOException {
        if (initializeDefault()) return;
        Path selected = location();
        if (selected != null) syncAll(selected, true);
    }

    private void syncAll(Path selected, boolean reconcileDeletions) throws IOException {
        for (Task task : tasks.findAll()) sync(selected, task.getId(), reconcileDeletions);
        Path index = indexDirectory(selected);
        if (!Files.isDirectory(index)) return;
        try (var entries = Files.list(index)) {
            for (Path entry : entries.filter(path -> path.getFileName().toString().matches("[0-9]+")).toList()) {
                long id = Long.parseLong(entry.getFileName().toString());
                if (!tasks.existsById(id)) sync(selected, id, reconcileDeletions);
            }
        }
    }

    public synchronized void sync(long taskId) throws IOException {
        if (initializeDefault()) return;
        Path selected = location();
        if (selected != null) sync(selected, taskId, true);
    }

    private boolean initializeDefault() throws IOException {
        if (defaultLocation == null || !Files.notExists(locationFile)) return false;
        Files.createDirectories(defaultLocation);
        select(defaultLocation);
        return true;
    }

    private void sync(Path selected, long id, boolean reconcileDeletions) throws IOException {
        Path root = selected.resolve("focus");
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The selected Focus folder is unavailable.");
        }

        Path index = indexDirectory(selected).resolve(Long.toString(id));
        Properties previous = new Properties();
        if (Files.exists(index)) {
            try (InputStream input = Files.newInputStream(index)) {
                previous.load(input);
            }
        }

        Task task = tasks.findById(id).orElse(null);
        Properties current = new Properties();
        if (task != null) {
            syncTask(root, previous, current, task, reconcileDeletions);
        }

        for (String key : previous.stringPropertyNames()) {
            String oldPath = previous.getProperty(key);
            if (current.containsValue(oldPath)) continue;
            Path path = managedPath(root, oldPath);
            String replacement = current.getProperty(key);
            if (replacement != null && Files.exists(path)
                    && Files.isSameFile(path, managedPath(root, replacement))) continue;
            Files.deleteIfExists(path);
            removeEmptyParents(root, path.getParent());
        }

        if (current.isEmpty()) {
            Files.deleteIfExists(index);
        } else if (!current.equals(previous)) {
            Files.createDirectories(index.getParent());
            Path temp = Files.createTempFile(index.getParent(), ".focus-", ".tmp");
            try {
                try (OutputStream output = Files.newOutputStream(temp)) {
                    current.store(output, null);
                }
                Files.move(temp, index, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
        }
    }

    private void syncTask(Path root, Properties previous, Properties current, Task task,
                          boolean reconcileDeletions) throws IOException {
        Long id = task.getId();
        String folder = fileComponent(task.getName()) + " [" + id + "]/";
        Map<Long, String> updateFolders = new HashMap<>();
        Map<Long, String> updateMessages = new HashMap<>();
        List<TimelineMessage> messages = new ArrayList<>();
        String description = task.getDescription() == null ? "" : task.getDescription();
        messages.add(new TimelineMessage(task.getCreatedAt(), "Task created", description));
        for (var update : progress.findByTaskIdOrderByRecordedAtDesc(id)) {
            String updateFolder = folder + "Progress update - " + STAMP.format(update.getRecordedAt()) + " [" + update.getId() + "]/";
            updateFolders.put(update.getId(), updateFolder);
            String message = update.getDescription();
            if (update.getReminder() != null && !update.getReminder().isBlank()) {
                message += "\n\nReminder\n" + update.getReminder();
            }
            updateMessages.put(update.getId(), message);
            messages.add(new TimelineMessage(update.getRecordedAt(), "Progress update", message));
        }

        String created = folder + "Task created - " + STAMP.format(task.getCreatedAt()) + "/";
        var events = activity.findByTaskIdOrderByOccurredAtDescIdDesc(id);
        events.stream()
                .filter(event -> event.getAction() != TaskActivity.Action.CREATED && event.getProgressUpdateId() == null)
                .map(event -> new TimelineMessage(event.getOccurredAt(), event.getSummary(), event.getNote()))
                .forEach(messages::add);
        writeText(root, current, "created", created + "message.txt", task.getName() + "\n\n" + description);
        if (task.getCompletedAt() != null) {
            String completion = task.getCompletionNote() == null ? "" : task.getCompletionNote();
            writeText(root, current, "completed",
                    folder + "Task completed - " + STAMP.format(task.getCompletedAt()) + "/message.txt", completion);
            if (events.stream().noneMatch(event -> event.getAction() == TaskActivity.Action.COMPLETED
                    && event.getProgressUpdateId() == null && event.getOccurredAt().equals(task.getCompletedAt()))) {
                messages.add(new TimelineMessage(task.getCompletedAt(), "Task completed", completion));
            }
        }
        String timeline = messages.stream().sorted(Comparator.comparing(TimelineMessage::time))
                .map(message -> STAMP.format(message.time()) + "  " + message.title()
                        + (message.text() == null || message.text().isBlank() ? "" : "\n" + message.text()))
                .collect(Collectors.joining("\n\n"));
        writeText(root, current, "timeline", folder + "timeline.txt", timeline);

        for (var update : progress.findByTaskIdOrderByRecordedAtDesc(id)) {
            String updateFolder = updateFolders.get(update.getId());
            String message = updateMessages.get(update.getId());
            String fullMessage = message
                    + "\n\n==================================================\n"
                    + "Messages (from task creation to the task end):\n"
                    + "==================================================\n\n"
                    + timeline;
            writeText(root, current, "message-" + update.getId(), updateFolder + "message.txt", fullMessage);
            writeText(root, current, "timeline-" + update.getId(), updateFolder + "timeline.txt", timeline);
        }

        for (var attachment : attachments.allFilesForTask(id)) {
            String key = "file-" + attachment.getStoredName();
            String oldPath = previous.getProperty(key);
            if (reconcileDeletions && oldPath != null && Files.notExists(managedPath(root, oldPath))) {
                attachments.deleteFile(id, attachment.getId());
                continue;
            }
            String targetFolder = updateFolders.getOrDefault(attachment.getProgressUpdateId(), created);
            String relativePath = targetFolder + "attachments/" + attachment.getId() + " - " + attachmentName(attachment.getOriginalName());
            Path target = managedPath(root, relativePath);
            ensureDirectory(target.getParent());
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                try (InputStream input = attachments.load(attachment).getInputStream()) {
                    Path temp = Files.createTempFile(target.getParent(), ".focus-", ".tmp");
                    try {
                        Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
                        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
                    } finally {
                        Files.deleteIfExists(temp);
                    }
                } catch (com.productivity.core.exception.ResourceNotFoundException ex) {
                    continue;
                }
            }
            current.setProperty(key, relativePath);
        }

        Map<String, List<ReferenceLink>> linksByFolder = links.findByTaskIdOrderByCreatedAtDesc(id).stream()
                .collect(Collectors.groupingBy(link -> updateFolders.getOrDefault(link.getProgressUpdateId(), created)));
        for (var group : linksByFolder.entrySet()) {
            List<ReferenceLink> references = group.getValue();
            Long updateId = references.get(0).getProgressUpdateId();
            String key = updateId == null ? "links-created" : "links-" + updateId;
            String oldPath = previous.getProperty(key);
            if (reconcileDeletions && oldPath != null && Files.notExists(managedPath(root, oldPath))) {
                links.deleteAll(references);
                continue;
            }
            String content = references.stream().map(link -> link.getLabel() + "\n" + link.getUrl())
                    .collect(Collectors.joining("\n\n"));
            writeText(root, current, key, group.getKey() + "links.txt", content);
        }
    }

    private record TimelineMessage(LocalDateTime time, String title, String text) {}

    private Path indexDirectory(Path selected) {
        var key = UUID.nameUUIDFromBytes(selected.toString().getBytes(StandardCharsets.UTF_8));
        return state.resolve(key.toString());
    }

    private static void writeText(Path root, Properties current, String key, String relative, String text) throws IOException {
        Path file = managedPath(root, relative);
        ensureDirectory(file.getParent());
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !Files.readString(file).equals(text)) {
            replace(file, text);
        }
        current.setProperty(key, relative);
    }

    private static void replace(Path target, String text) throws IOException {
        Path temp = Files.createTempFile(target.getParent(), ".focus-", ".tmp");
        try {
            Files.writeString(temp, text);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String attachmentName(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || name.length() - dot > 12) return fileComponent(name);
        return fileComponent(name.substring(0, dot)) + "." + fileComponent(name.substring(dot + 1));
    }

    private static String fileComponent(String name) {
        String clean = name.replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_").strip().replaceAll("[. ]+$", "");
        if (clean.isEmpty()) return "Untitled";
        int length = clean.codePointCount(0, clean.length());
        return clean.substring(0, clean.offsetByCodePoints(0, Math.min(55, length)));
    }

    private static Path managedPath(Path root, String relative) throws IOException {
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root) || path.equals(root)) throw new IOException("Invalid Focus storage path.");
        for (Path part = path; part != null && !part.equals(root); part = part.getParent()) {
            if (Files.isSymbolicLink(part)) throw new IOException("Focus storage paths cannot contain symbolic links.");
        }
        return path;
    }

    private static void ensureDirectory(Path path) throws IOException {
        Files.createDirectories(path);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Could not open the Focus folder.");
    }

    private static void removeEmptyParents(Path root, Path directory) throws IOException {
        for (Path path = directory; !path.equals(root); path = path.getParent()) {
            try {
                Files.deleteIfExists(path);
            } catch (java.nio.file.DirectoryNotEmptyException ex) {
                return;
            }
        }
    }
}
