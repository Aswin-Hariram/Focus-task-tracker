package com.productivity.app.web;

import com.productivity.app.service.BackupService;
import com.productivity.attachment.entity.Attachment;
import com.productivity.attachment.entity.ReferenceLink;
import com.productivity.attachment.service.AttachmentService;
import com.productivity.category.entity.Category;
import com.productivity.category.service.CategoryService;
import com.productivity.core.security.SecurityUtils;
import com.productivity.progress.entity.ProgressUpdate;
import com.productivity.progress.service.ProgressService;
import com.productivity.task.entity.Task;
import com.productivity.task.entity.TaskActivity;
import com.productivity.task.entity.TaskActivity.Action;
import com.productivity.task.repository.TaskActivityRepository;
import com.productivity.task.enums.Priority;
import com.productivity.task.enums.TaskStatus;
import com.productivity.task.service.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/tasks")
public class TaskController {
    private final TaskService tasks;
    private final CategoryService categories;
    private final ProgressService progress;
    private final AttachmentService attachments;
    private final TaskActivityRepository activity;
    private final BackupService workspaceData;

    public TaskController(TaskService tasks, CategoryService categories, ProgressService progress,
                          AttachmentService attachments, TaskActivityRepository activity,
                          BackupService workspaceData) {
        this.tasks = tasks;
        this.categories = categories;
        this.progress = progress;
        this.attachments = attachments;
        this.activity = activity;
        this.workspaceData = workspaceData;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String query,
                       @RequestParam(required = false) Long category,
                       @RequestParam(required = false) TaskStatus status,
                       @RequestParam(required = false) Priority priority,
                       @RequestParam(defaultValue = "due") String sort,
                       @RequestParam(defaultValue = "1") int page,
                       Model model) {
        var matches = tasks.search(query, category, null, priority);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (var value : TaskStatus.values()) {
            counts.put(value.name(), 0L);
        }
        matches.forEach(task -> counts.merge(task.getStatus().name(), 1L, Long::sum));
        var order = "recent".equals(sort)
                ? Comparator.comparing(Task::getCreatedAt).reversed()
                : Comparator.comparing(Task::getDeadline, Comparator.nullsLast(
                        "due-desc".equals(sort) ? Comparator.<LocalDate>reverseOrder() : Comparator.naturalOrder()));
        var filtered = matches.stream()
                .filter(task -> status == null || task.getStatus() == status)
                .sorted(order)
                .toList();

        int pageSize = 6;
        int pageCount = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
        int currentPage = Math.max(1, Math.min(page, pageCount));
        int start = (currentPage - 1) * pageSize;
        int end = Math.min(start + pageSize, filtered.size());

        model.addAttribute("pageTitle", "Tasks");
        model.addAttribute("tasks", filtered.subList(start, end));
        model.addAttribute("taskCounts", counts);
        model.addAttribute("totalTasks", matches.size());
        model.addAttribute("resultCount", filtered.size());
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("pageCount", pageCount);
        model.addAttribute("pageStart", Math.max(1, currentPage - 2));
        model.addAttribute("pageEnd", Math.min(pageCount, currentPage + 2));
        model.addAttribute("firstResult", start + 1);
        model.addAttribute("lastResult", end);
        model.addAttribute("sort", sort);
        model.addAttribute("query", query);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedPriority", priority);
        model.addAttribute("categories", categories.findActive());
        model.addAttribute("statuses", TaskStatus.values());
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("deletedTasks", workspaceData.deletedTasks());
        return "tasks/list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        TaskForm form = new TaskForm(); form.setPlannedDate(java.time.LocalDate.now());
        model.addAttribute("taskForm", form); prepareForm(model, "Create task"); return "tasks/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("taskForm", TaskForm.from(tasks.get(id))); prepareForm(model, "Edit task"); return "tasks/form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute TaskForm taskForm, BindingResult result,
                       HttpServletRequest request, Model model, RedirectAttributes redirect,
                       @RequestParam(required = false) List<Long> removeFileIds,
                       @RequestParam(required = false) List<Long> removeLinkIds) {
        List<TaskLinkInput> linkInputs = new ArrayList<>();
        String[] urls = request.getParameterValues("linkUrls");
        String[] labels = request.getParameterValues("linkLabels");
        if (urls != null) {
            for (int i = 0; i < urls.length; i++) {
                linkInputs.add(new TaskLinkInput(urls[i], labels != null && i < labels.length ? labels[i] : ""));
            }
        }
        if (taskForm.getLinkUrl() != null && !taskForm.getLinkUrl().isBlank()) {
            linkInputs.add(new TaskLinkInput(taskForm.getLinkUrl(), taskForm.getLinkLabel()));
        }
        model.addAttribute("taskLinkInputs", linkInputs.isEmpty() ? List.of(new TaskLinkInput("", "")) : linkInputs);
        var linksToSave = linkInputs.stream().filter(link -> !link.url().isBlank()).toList();
        try {
            linksToSave.forEach(link -> attachments.validateLink(link.label(), link.url()));
        } catch (IllegalArgumentException ex) {
            result.reject("task.link.invalid", ex.getMessage());
        }
        List<Long> filesToRemove = removeFileIds == null ? List.of() : removeFileIds.stream().distinct().toList();
        List<Long> linksToRemove = removeLinkIds == null ? List.of() : removeLinkIds.stream().distinct().toList();
        model.addAttribute("removeFileIds", filesToRemove);
        model.addAttribute("removeLinkIds", linksToRemove);
        Long taskId = taskForm.getId();
        if (!filesToRemove.isEmpty() && (taskId == null ||
                !attachments.filesForTask(taskId).stream().map(Attachment::getId).toList().containsAll(filesToRemove))) {
            result.reject("task.files.invalid", "One or more files could not be removed from this task.");
        }
        if (!linksToRemove.isEmpty() && (taskId == null ||
                !attachments.linksForTask(taskId).stream().map(link -> link.getId()).toList().containsAll(linksToRemove))) {
            result.reject("task.links.invalid", "One or more links could not be removed from this task.");
        }
        if (result.hasErrors()) { prepareForm(model, taskForm.getId() == null ? "Create task" : "Edit task"); return "tasks/form"; }
        Task task = taskForm.getId() == null ? new Task() : tasks.get(taskForm.getId());
        task.setName(taskForm.getName()); task.setDescription(taskForm.getDescription()); task.setPlannedDate(taskForm.getPlannedDate());
        task.setDeadline(taskForm.getDeadline()); task.setPriority(taskForm.getPriority());
        task.setCategory(categories.get(taskForm.getCategoryId()));
        try {
            task = tasks.save(task, taskForm.getStatus(), taskForm.getCompletionNote());
            List<MultipartFile> taskAttachments = new ArrayList<>();
            if (taskForm.getAttachments() != null) {
                taskAttachments.addAll(taskForm.getAttachments());
            }
            if (taskForm.getAttachment() != null && !taskForm.getAttachment().isEmpty()) {
                taskAttachments.add(taskForm.getAttachment());
            }
            for (MultipartFile f : taskAttachments) {
                if (f != null && !f.isEmpty()) {
                    attachments.store(task.getId(), f);
                }
            }
            for (TaskLinkInput link : linksToSave) {
                attachments.addLink(task.getId(), link.label(), link.url());
            }
            for (Long fileId : filesToRemove) {
                attachments.deleteFile(task.getId(), fileId);
            }
            for (Long linkId : linksToRemove) {
                attachments.deleteLink(task.getId(), linkId);
            }
        } catch (IllegalArgumentException | IOException ex) {
            result.reject("task.invalid", ex.getMessage());
            prepareForm(model, taskForm.getId() == null ? "Create task" : "Edit task");
            return "tasks/form";
        }
        redirect.addFlashAttribute("success", taskForm.getId() == null ? "Task created" : "Task updated");
        return "redirect:/tasks/" + task.getId();
    }

    public record TaskLinkInput(String url, String label) {}

    public record PastIterationView(int iterationNumber, java.time.LocalDateTime startedAt, java.time.LocalDateTime completedAt, String completionNote, java.util.List<ProgressUpdate> updates) {}

    public record TimelineEntry(LocalDateTime time, String title, String icon, String tone,
                                String description, ProgressUpdate update, String editPath, TaskStatus status) {}

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("pageTitle", "Task details");
        Task task = tasks.get(id);
        model.addAttribute("task", task);

        var allUpdates = progress.forTask(id);
        List<TaskActivity> taskEvents = activity.findByTaskIdOrderByOccurredAtDescIdDesc(id);
        var linkedUpdates = taskEvents.stream().map(TaskActivity::getProgressUpdateId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, java.util.List<Attachment>> progressFiles = new LinkedHashMap<>();
        Map<Long, List<ReferenceLink>> progressLinks = new LinkedHashMap<>();
        allUpdates.forEach(update -> {
            progressFiles.put(update.getId(), attachments.filesForProgress(update.getId()));
            progressLinks.put(update.getId(), attachments.linksForProgress(update.getId()));
        });

        var storedIterations = tasks.iterationsForTask(id);
        java.util.List<PastIterationView> pastHistory = new java.util.ArrayList<>();
        for (int i = 1; i < task.getIteration(); i++) {
            final int iterNum = i;
            var stored = storedIterations.stream().filter(it -> it.getIterationNumber() == iterNum).findFirst().orElse(null);
            var iterUpdates = allUpdates.stream()
                    .filter(update -> update.getIteration() == iterNum && !linkedUpdates.contains(update.getId())).toList();
            pastHistory.add(new PastIterationView(
                    iterNum,
                    stored != null ? stored.getStartedAt() : task.getCreatedAt(),
                    stored != null ? stored.getCompletedAt() : null,
                    stored != null ? stored.getCompletionNote() : null,
                    iterUpdates
            ));
        }

        var timelineUpdates = allUpdates.stream()
                .filter(update -> update.getIteration() == task.getIteration() || linkedUpdates.contains(update.getId())).toList();
        model.addAttribute("timeline", taskTimeline(task, timelineUpdates, taskEvents));
        model.addAttribute("pastHistory", pastHistory);
        model.addAttribute("progressFiles", progressFiles);
        model.addAttribute("progressLinks", progressLinks);
        model.addAttribute("progressForm", new ProgressUpdate());
        model.addAttribute("files", attachments.filesForTask(id));
        model.addAttribute("links", attachments.linksForTask(id));
        model.addAttribute("statuses", TaskStatus.values());
        model.addAttribute("timeLogs", tasks.timeLogsForTask(id));
        long currentSession = task.getActiveSessionSeconds(LocalDateTime.now());
        model.addAttribute("timerCurrentDisplay", Task.formatTimerDigits(currentSession));
        model.addAttribute("timerTotalDisplay", Task.formatTimerDigits(task.getTotalTimeSpentSeconds() + currentSession));
        return "tasks/detail";
    }

    private List<TimelineEntry> taskTimeline(Task task, List<ProgressUpdate> updates, List<TaskActivity> events) {
        List<TaskActivity> taskEvents = events.stream().filter(event -> event.getAction() != Action.DELETED).toList();

        List<TimelineEntry> timeline = new ArrayList<>();
        Map<Long, ProgressUpdate> standaloneUpdates = new LinkedHashMap<>();
        updates.forEach(update -> standaloneUpdates.put(update.getId(), update));
        String progressPath = "/tasks/" + task.getId() + "/progress/";

        for (TaskActivity event : taskEvents) {
            String description = activityDescription(event);
            ProgressUpdate update = standaloneUpdates.remove(event.getProgressUpdateId());
            timeline.add(new TimelineEntry(event.getOccurredAt(), event.getSummary(), event.getAction().getIcon(),
                    event.getAction().name(), update == null ? description : update.getDescription(), update,
                    progressPath + "activity/" + event.getId() + "/edit",
                    event.getAction() == Action.COMPLETED ? TaskStatus.COMPLETED
                            : event.getAction() == Action.STATUS_CHANGED ? event.getStatus() : null));
        }
        if (task.getCreatedAt() != null
                && taskEvents.stream().noneMatch(event -> event.getAction() == Action.CREATED)) {
            timeline.add(new TimelineEntry(task.getCreatedAt(), Action.CREATED.getTitle(),
                    Action.CREATED.getIcon(), Action.CREATED.name(), "Created in " + task.getCategory().getName(), null,
                    progressPath + "event/CREATED/edit", null));
        }
        if (task.getCompletedAt() != null && taskEvents.stream().noneMatch(event ->
                event.getAction() == Action.COMPLETED && event.getOccurredAt().equals(task.getCompletedAt()))) {
            timeline.add(new TimelineEntry(task.getCompletedAt(), Action.COMPLETED.getTitle(),
                    Action.COMPLETED.getIcon(), Action.COMPLETED.name(), task.getCompletionNote(), null,
                    progressPath + "event/COMPLETED/edit", TaskStatus.COMPLETED));
        }
        for (ProgressUpdate update : standaloneUpdates.values()) {
            timeline.add(new TimelineEntry(update.getRecordedAt(), "Progress update",
                    "message-square", "PROGRESS", update.getDescription(), update, progressPath + update.getId() + "/edit", null));
        }
        timeline.sort(Comparator.comparing(TimelineEntry::time).reversed());
        return timeline;
    }

    private String activityDescription(TaskActivity event) {
        return switch (event.getAction()) {
            case CREATED -> "Created in " + event.getCategoryName();
            case COMPLETED -> event.getNote();
            case STATUS_CHANGED -> event.getPreviousStatus() == null ? null : "From " + event.getPreviousStatus().getLabel();
            default -> null;
        };
    }

    @PostMapping("/{id}/duplicate")
    public String duplicate(@PathVariable Long id, RedirectAttributes redirect) {
        Task copy = tasks.duplicate(id);
        redirect.addFlashAttribute("success", "Task duplicated");
        return "redirect:/tasks/" + copy.getId();
    }

    @PostMapping("/{id}/rework")
    public String rework(@PathVariable Long id,
                         @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate plannedDate,
                         @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate deadline,
                         RedirectAttributes redirect) {
        try {
            Task reworked = tasks.startRework(id, plannedDate != null ? plannedDate : java.time.LocalDate.now(), deadline);
            redirect.addFlashAttribute("success", "Task rework started (Iteration " + reworked.getIteration() + "). Description and files are preserved.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/tasks/" + id;
    }

    @PostMapping("/{id}/status")
    public Object status(@PathVariable Long id, @RequestParam TaskStatus status,
                         @RequestParam(required = false) String completionNote,
                         @RequestParam(required = false) List<MultipartFile> files,
                         @RequestParam(required = false) List<MultipartFile> file,
                         @RequestParam(required = false) List<String> linkUrls,
                         @RequestParam(required = false) List<String> linkLabels,
                         @RequestParam(required = false) String linkUrl,
                         @RequestParam(required = false) String linkLabel,
                         @RequestParam(required = false) String redirectUrl,
                         HttpServletRequest request,
                         RedirectAttributes redirect) {
        tasks.changeStatus(id, status, completionNote);

        if (status == TaskStatus.COMPLETED) {
            List<MultipartFile> allFiles = new java.util.ArrayList<>();
            if (request instanceof org.springframework.web.multipart.MultipartHttpServletRequest multipartRequest) {
                multipartRequest.getMultiFileMap().values().forEach(allFiles::addAll);
            } else {
                if (files != null) allFiles.addAll(files);
                if (file != null) allFiles.addAll(file);
            }

            for (MultipartFile f : allFiles) {
                if (f != null && !f.isEmpty()) {
                    try {
                        attachments.storeCompletionAttachment(id, f);
                    } catch (IOException | IllegalArgumentException ex) {
                        if (redirect != null) {
                            redirect.addFlashAttribute("error", "Error uploading file: " + ex.getMessage());
                        }
                    }
                }
            }

            List<String> urls = new java.util.ArrayList<>();
            List<String> labels = new java.util.ArrayList<>();
            if (linkUrls != null) {
                urls.addAll(linkUrls);
                if (linkLabels != null) {
                    labels.addAll(linkLabels);
                }
            }
            if (linkUrl != null && !linkUrl.isBlank()) {
                urls.add(linkUrl);
                labels.add(linkLabel);
            }

            for (int i = 0; i < urls.size(); i++) {
                String u = urls.get(i);
                if (u != null && !u.isBlank()) {
                    String lbl = (i < labels.size() && labels.get(i) != null && !labels.get(i).isBlank())
                            ? labels.get(i).trim()
                            : u.trim();
                    try {
                        attachments.addLink(id, lbl, u.trim());
                    } catch (IllegalArgumentException ex) {
                        if (redirect != null) {
                            redirect.addFlashAttribute("error", "Error adding link: " + ex.getMessage());
                        }
                    }
                }
            }
        }

        String accept = request != null ? request.getHeader("Accept") : null;
        String requestedWith = request != null ? request.getHeader("X-Requested-With") : null;
        boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(requestedWith) || (accept != null && accept.contains("application/json"));

        if (isAjax) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "id", id,
                    "status", status.name(),
                    "statusLabel", status.getLabel()
            ));
        }

        if (redirect != null) {
            redirect.addFlashAttribute("success", status == TaskStatus.COMPLETED
                    ? "Task marked as completed with completion details"
                    : "Task status updated");
        }
        if (redirectUrl != null && !redirectUrl.isBlank()) {
            return "redirect:" + SecurityUtils.sanitizeRedirectUrl(redirectUrl, "/tasks/" + id);
        }
        return "redirect:/tasks/" + id;
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable Long id, RedirectAttributes redirect) {
        tasks.archive(id); redirect.addFlashAttribute("success", "Task archived"); return "redirect:/tasks";
    }

    @PostMapping("/{id}/delete")
    public Object delete(@PathVariable Long id,
                         @RequestParam(required = false) String redirectUrl,
                         RedirectAttributes redirect,
                         HttpServletRequest request) {
        try {
            String taskName = workspaceData.deleteTask(id);

            boolean isAjax = request != null && ("XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With")) ||
                    (request.getHeader("Accept") != null && request.getHeader("Accept").contains("application/json")));

            if (isAjax) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "taskId", id,
                        "deletedTaskId", id,
                        "taskName", taskName,
                        "deletedTaskName", taskName,
                        "undoUrl", "/tasks/" + id + "/undo-delete",
                        "message", "Task deleted"
                ));
            }

            if (redirect != null) {
                redirect.addFlashAttribute("success", "Task deleted");
                redirect.addFlashAttribute("deletedTaskId", id);
                redirect.addFlashAttribute("deletedTaskName", taskName);
            }
        } catch (IOException ex) {
            if (redirect != null) {
                redirect.addFlashAttribute("error", "Error removing task files: " + ex.getMessage());
            }
        }
        if (redirectUrl != null && !redirectUrl.isBlank()) {
            return "redirect:" + SecurityUtils.sanitizeRedirectUrl(redirectUrl, "/tasks");
        }
        return "redirect:/tasks";
    }

    @PostMapping("/{id}/undo-delete")
    public Object undoDelete(@PathVariable Long id,
                             HttpServletRequest request,
                             RedirectAttributes redirect) {
        String accept = request != null ? request.getHeader("Accept") : null;
        String requestedWith = request != null ? request.getHeader("X-Requested-With") : null;
        String fetchMode = request != null ? request.getHeader("Sec-Fetch-Mode") : null;
        boolean isHtmlFormSubmission = accept != null && accept.startsWith("text/html") && !"XMLHttpRequest".equalsIgnoreCase(requestedWith);
        boolean isAjax = !isHtmlFormSubmission && (
                "XMLHttpRequest".equalsIgnoreCase(requestedWith) ||
                (accept != null && (accept.contains("application/json") || accept.contains("*/*"))) ||
                "cors".equalsIgnoreCase(fetchMode) ||
                "same-origin".equalsIgnoreCase(fetchMode)
        );

        Task saved;
        try {
            saved = workspaceData.undoTaskDeletion(id);
        } catch (IOException ex) {
            String message = "Could not restore the task and its attachments.";
            if (isAjax) return ResponseEntity.internalServerError().body(Map.of("success", false, "message", message));
            redirect.addFlashAttribute("error", message);
            return "redirect:/tasks";
        }
        if (saved == null) {
            if (isAjax) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Cannot undo deletion. Task is no longer in the recycle bin."
                ));
            }
            if (redirect != null) {
                redirect.addFlashAttribute("error", "Cannot undo deletion. Task is no longer in the recycle bin.");
            }
            return "redirect:/tasks";
        }

        if (isAjax) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "restored", true,
                    "id", saved.getId(),
                    "taskId", saved.getId(),
                    "name", saved.getName(),
                    "taskName", saved.getName(),
                    "message", "Task \"" + saved.getName() + "\" restored successfully"
            ));
        }

        if (redirect != null) {
            redirect.addFlashAttribute("info", "Task \"" + saved.getName() + "\" restored.");
        }
        return "redirect:/tasks/" + saved.getId();
    }

    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/{id}/progress")
    public Object addProgress(@PathVariable Long id,
                              @ModelAttribute("progressForm") ProgressUpdate update,
                              BindingResult result,
                              @RequestParam(required = false) TaskStatus status,
                              @RequestParam(defaultValue = "false") boolean keepCurrentStatus,
                              @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
                              @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime recordedAt,
                              @RequestParam(required = false) Long before,
                              @RequestParam(required = false) Long after,
                              @RequestParam(required = false) List<MultipartFile> files,
                              @RequestParam(required = false) List<MultipartFile> file,
                              @RequestParam(required = false) List<String> linkUrls,
                              @RequestParam(required = false) List<String> linkLabels,
                              @RequestParam(required = false) String linkUrl,
                              @RequestParam(required = false) String linkLabel,
                              @RequestParam(required = false) String redirectUrl,
                              HttpServletRequest request,
                              RedirectAttributes redirect) {
        String accept = request != null ? request.getHeader("Accept") : null;
        String requestedWith = request != null ? request.getHeader("X-Requested-With") : null;
        boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(requestedWith) || (accept != null && accept.contains("application/json"));

        if (update.getDescription() == null || update.getDescription().isBlank()) {
            if (status == TaskStatus.IN_PROGRESS) {
                update.setDescription("Moved to In Progress");
            } else if (status == TaskStatus.YET_TO_START) {
                update.setDescription("Yet to apply");
            } else if (status == TaskStatus.COMPLETED) {
                update.setDescription("Completed");
            } else if (status == TaskStatus.BLOCKED) {
                update.setDescription("Blocked/Closed");
            } else if (status != null) {
                update.setDescription("Moved to " + status.getLabel());
            } else {
                update.setDescription("Progress update logged");
            }
        }

        if (update.getDescription().length() > 3000) {
            String errorMsg = "Progress description must be under 3,000 characters";
            if (isAjax) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", errorMsg));
            }
            if (redirect != null) {
                redirect.addFlashAttribute("error", errorMsg);
            }
            if (redirectUrl != null && !redirectUrl.isBlank()) {
                return "redirect:" + SecurityUtils.sanitizeRedirectUrl(redirectUrl, "/tasks/" + id);
            }
            return "redirect:/tasks/" + id;
        }

        if (status != null) {
            String note = (status == TaskStatus.COMPLETED) ? update.getDescription() : null;
            if (date != null) {
                tasks.placeOnBoard(id, date, status, before, after);
                if (status == TaskStatus.COMPLETED && note != null && !note.isBlank()) {
                    tasks.changeStatus(id, status, note);
                }
            } else {
                tasks.changeStatus(id, status, note);
            }
        } else if (!keepCurrentStatus) {
            Task task = tasks.get(id);
            if (task.getStatus() == TaskStatus.YET_TO_START) {
                tasks.changeStatus(id, TaskStatus.IN_PROGRESS, null);
            }
        }

        update.setId(null);
        if (recordedAt != null) {
            update.setRecordedAt(recordedAt);
        }
        ProgressUpdate savedUpdate = progress.add(id, update);

        List<MultipartFile> allFiles = new java.util.ArrayList<>();
        if (request instanceof org.springframework.web.multipart.MultipartHttpServletRequest multipartRequest) {
            multipartRequest.getMultiFileMap().values().forEach(allFiles::addAll);
        } else {
            if (files != null) allFiles.addAll(files);
            if (file != null) allFiles.addAll(file);
        }

        for (MultipartFile f : allFiles) {
            if (f != null && !f.isEmpty()) {
                try {
                    attachments.storeProgressAttachment(id, savedUpdate.getId(), f);
                } catch (IOException | IllegalArgumentException ex) {
                    if (redirect != null) {
                        redirect.addFlashAttribute("error", "Error uploading file: " + ex.getMessage());
                    }
                }
            }
        }

        List<String> urls = new java.util.ArrayList<>();
        List<String> labels = new java.util.ArrayList<>();
        if (linkUrls != null) {
            urls.addAll(linkUrls);
            if (linkLabels != null) {
                labels.addAll(linkLabels);
            }
        }
        if (linkUrl != null && !linkUrl.isBlank()) {
            urls.add(linkUrl);
            labels.add(linkLabel);
        }

        for (int i = 0; i < urls.size(); i++) {
            String u = urls.get(i);
            if (u != null && !u.isBlank()) {
                String lbl = (i < labels.size() && labels.get(i) != null && !labels.get(i).isBlank())
                        ? labels.get(i).trim()
                        : u.trim();
                try {
                    attachments.addProgressLink(id, savedUpdate.getId(), lbl, u.trim());
                } catch (IllegalArgumentException ex) {
                    if (redirect != null) {
                        redirect.addFlashAttribute("error", "Error adding link: " + ex.getMessage());
                    }
                }
            }
        }

        if (isAjax) {
            Task currentTask = tasks.get(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "taskId", id,
                    "status", currentTask.getStatus().name(),
                    "statusLabel", currentTask.getStatus().getLabel(),
                    "progressId", savedUpdate.getId(),
                    "description", savedUpdate.getDescription(),
                    "updateCount", progress.forTask(id).size()
            ));
        }

        if (redirect != null) {
            redirect.addFlashAttribute("success", "Progress update added");
        }
        if (redirectUrl != null && !redirectUrl.isBlank()) {
            return "redirect:" + SecurityUtils.sanitizeRedirectUrl(redirectUrl, "/tasks/" + id);
        }
        return "redirect:/tasks/" + id;
    }

    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/{taskId}/progress/activity/{activityId}/edit")
    public Object editActivityProgress(@PathVariable Long taskId, @PathVariable Long activityId,
                                       @RequestParam String description, @RequestParam(required = false) String reminder,
                                       @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime recordedAt,
                                       HttpServletRequest request, RedirectAttributes redirect) {
        var event = activity.findById(activityId)
                .filter(item -> taskId.equals(item.getTaskId()) && item.getAction() != Action.DELETED);
        if (event.isEmpty()) {
            boolean isAjax = request != null && ("XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))
                    || (request.getHeader("Accept") != null && request.getHeader("Accept").contains("application/json")));
            if (isAjax) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Task activity could not be found."));
            }
            redirect.addFlashAttribute("error", "Task activity could not be found.");
            return "redirect:/tasks/" + taskId;
        }
        return saveProgressEdit(taskId, event.get().getProgressUpdateId(), event.get(), description, reminder, recordedAt, request, redirect);
    }

    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/{taskId}/progress/event/{action}/edit")
    public Object editUnrecordedProgress(@PathVariable Long taskId, @PathVariable Action action,
                                         @RequestParam String description, @RequestParam(required = false) String reminder,
                                         @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime recordedAt,
                                         HttpServletRequest request, RedirectAttributes redirect) {
        Task task = tasks.get(taskId);
        LocalDateTime occurredAt = switch (action) {
            case CREATED -> task.getCreatedAt();
            case COMPLETED -> task.getCompletedAt();
            default -> null;
        };
        if (occurredAt == null) {
            boolean isAjax = request != null && ("XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))
                    || (request.getHeader("Accept") != null && request.getHeader("Accept").contains("application/json")));
            if (isAjax) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Task activity could not be found."));
            }
            redirect.addFlashAttribute("error", "Task activity could not be found.");
            return "redirect:/tasks/" + taskId;
        }
        TaskActivity event = activity.findByTaskIdOrderByOccurredAtDescIdDesc(taskId).stream()
                .filter(item -> item.getAction() == action && occurredAt.equals(item.getOccurredAt()))
                .findFirst().orElseGet(() -> new TaskActivity(taskId, task.getName(), task.getCategory().getId(),
                        task.getCategory().getName(), task.getCategory().getColor(), action, task.getStatus(), null,
                        occurredAt, action == Action.COMPLETED ? task.getCompletionNote() : null));
        return saveProgressEdit(taskId, event.getProgressUpdateId(), event, description, reminder, recordedAt, request, redirect);
    }

    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/{taskId}/progress/{progressId}/edit")
    public Object editProgress(@PathVariable Long taskId, @PathVariable Long progressId,
                               @RequestParam String description, @RequestParam(required = false) String reminder,
                               @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime recordedAt,
                               HttpServletRequest request, RedirectAttributes redirect) {
        return saveProgressEdit(taskId, progressId, null, description, reminder, recordedAt, request, redirect);
    }

    private Object saveProgressEdit(Long taskId, Long progressId, TaskActivity event, String description,
                                    String reminder, LocalDateTime recordedAt, HttpServletRequest request, RedirectAttributes redirect) {
        boolean isAjax = request != null && ("XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))
                || (request.getHeader("Accept") != null && request.getHeader("Accept").contains("application/json")));
        try {
            if (progressId != null) {
                progress.requireBelongsToTask(progressId, taskId);
                if (event == null) {
                    Long updateId = progressId;
                    event = activity.findByTaskIdOrderByOccurredAtDescIdDesc(taskId).stream()
                            .filter(item -> updateId.equals(item.getProgressUpdateId()))
                            .findFirst().orElse(null);
                }
            }
            String confirmedCompletion = request.getParameter("confirmedCompletion");
            boolean movingInProgress = event != null && event.getAction() == Action.STATUS_CHANGED
                    && event.getStatus() == TaskStatus.IN_PROGRESS && recordedAt != null;
            Task reopening = null;
            if (movingInProgress) {
                Task task = tasks.lockForTimelineEdit(taskId);
                if (task.getStatus() == TaskStatus.COMPLETED && task.getCompletedAt() != null
                        && recordedAt.isAfter(task.getCompletedAt())) {
                    reopening = task;
                }
            }
            if (reopening != null && !reopening.getCompletedAt().toString().equals(confirmedCompletion)) {
                String message = "Confirm reopening this task as iteration " + (reopening.getIteration() + 1) + ".";
                return timelineEditConflict(message, taskId, isAjax, redirect);
            }
            if (reopening == null && confirmedCompletion != null && !confirmedCompletion.isBlank()) {
                return timelineEditConflict("The task changed. Review its timeline before saving again.", taskId, isAjax, redirect);
            }
            if (reopening != null) progressId = null;
            LocalDateTime progressTime = recordedAt != null || event == null ? recordedAt : event.getOccurredAt();
            List<ReferenceLink> savedLinks = progressId == null ? List.of() : attachments.linksForProgress(progressId);
            Map<Long, TaskLinkInput> editedLinks = new LinkedHashMap<>();
            for (ReferenceLink link : savedLinks) {
                String url = request.getParameter("existingLinkUrl." + link.getId());
                if (url != null && request.getParameter("removeLink." + link.getId()) == null) {
                    String label = request.getParameter("existingLinkLabel." + link.getId());
                    attachments.validateLink(label, url);
                    editedLinks.put(link.getId(), new TaskLinkInput(url, label));
                }
            }
            List<TaskLinkInput> newLinks = new ArrayList<>();
            String[] urls = request.getParameterValues("linkUrls");
            String[] labels = request.getParameterValues("linkLabels");
            if (urls != null) {
                for (int i = 0; i < urls.length; i++) {
                    if (urls[i].isBlank()) continue;
                    String label = labels != null && i < labels.length ? labels[i] : null;
                    attachments.validateLink(label, urls[i]);
                    newLinks.add(new TaskLinkInput(urls[i], label));
                }
            }

            var multipart = org.springframework.web.util.WebUtils.getNativeRequest(request,
                    org.springframework.web.multipart.MultipartHttpServletRequest.class);
            boolean hasNewFiles = multipart != null && multipart.getFiles("files").stream().anyMatch(file -> !file.isEmpty());
            boolean dateOnly = event != null && progressId == null && description.isBlank()
                    && (reminder == null || reminder.isBlank()) && newLinks.isEmpty() && !hasNewFiles;
            if (reopening != null) {
                event = reopenAt(reopening, recordedAt);
            } else if (event != null && recordedAt != null) {
                tasks.correctMilestoneDate(event, recordedAt);
                activity.save(event);
            }
            String successMessage = reopening == null ? "Progress update saved" : "Task reopened as iteration " + reopening.getIteration();
            if (dateOnly) {
                String message = reopening == null ? "Timeline date saved" : successMessage;
                if (isAjax) return ResponseEntity.ok(Map.of("success", true, "message", message));
                redirect.addFlashAttribute("success", message);
                return "redirect:/tasks/" + taskId;
            }

            if (progressId == null) {
                progressId = progress.addForActivity(taskId, progressTime, description, reminder).getId();
                if (event != null) {
                    event.setProgressUpdateId(progressId);
                    activity.save(event);
                }
            } else {
                progress.edit(taskId, progressId, description, reminder, progressTime);
            }
            List<Attachment> savedFiles = attachments.filesForProgress(progressId);
            if (multipart != null) {
                for (MultipartFile file : multipart.getFiles("files")) {
                    if (!file.isEmpty()) attachments.storeProgressAttachment(taskId, progressId, file);
                }
                for (Attachment file : savedFiles) {
                    MultipartFile replacement = multipart.getFile("replaceFile." + file.getId());
                    if (replacement != null && !replacement.isEmpty()
                            && request.getParameter("removeFile." + file.getId()) == null) {
                        attachments.storeProgressAttachment(taskId, progressId, replacement);
                        attachments.deleteFile(taskId, file.getId());
                    }
                }
            }
            for (Attachment file : savedFiles) {
                if (request.getParameter("removeFile." + file.getId()) != null) {
                    attachments.deleteFile(taskId, file.getId());
                }
            }
            for (var entry : editedLinks.entrySet()) {
                TaskLinkInput link = entry.getValue();
                attachments.editProgressLink(taskId, progressId, entry.getKey(), link.label(), link.url());
            }
            for (TaskLinkInput link : newLinks) {
                attachments.addProgressLink(taskId, progressId, link.label(), link.url());
            }
            for (ReferenceLink link : savedLinks) {
                if (request.getParameter("removeLink." + link.getId()) != null) {
                    attachments.deleteLink(taskId, link.getId());
                }
            }
            if (isAjax) {
                return ResponseEntity.ok(Map.of("success", true, "message", successMessage, "progressId", progressId));
            }
            redirect.addFlashAttribute("success", successMessage);
        } catch (IOException | IllegalArgumentException ex) {
            org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            if (isAjax) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
            }
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/tasks/" + taskId;
    }

    private Object timelineEditConflict(String message, Long taskId, boolean isAjax, RedirectAttributes redirect) {
        if (isAjax) return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("success", false, "error", message));
        redirect.addFlashAttribute("error", message);
        return "redirect:/tasks/" + taskId;
    }

    private TaskActivity reopenAt(Task task, LocalDateTime startedAt) {
        Long taskId = task.getId();
        LocalDateTime completedAt = task.getCompletedAt();
        boolean hasCompletion = activity.findByTaskIdOrderByOccurredAtDescIdDesc(taskId).stream()
                .anyMatch(event -> event.getAction() == Action.COMPLETED && completedAt.equals(event.getOccurredAt()));
        if (!hasCompletion) {
            activity.save(new TaskActivity(taskId, task.getName(), task.getCategory().getId(), task.getCategory().getName(),
                    task.getCategory().getColor(), Action.COMPLETED, TaskStatus.COMPLETED, null,
                    completedAt, task.getCompletionNote()));
        }
        long previousEventId = activity.findFirstByTaskIdOrderByIdDesc(taskId).map(TaskActivity::getId).orElse(0L);
        tasks.reopenFromTimeline(task, startedAt);
        // PostgreSQL records the status change when the task update is flushed.
        TaskActivity reopened = activity.findFirstByTaskIdOrderByIdDesc(taskId)
                .filter(event -> event.getId() > previousEventId && event.getAction() == Action.STATUS_CHANGED
                        && event.getPreviousStatus() == TaskStatus.COMPLETED && event.getStatus() == TaskStatus.IN_PROGRESS)
                .orElseGet(() -> new TaskActivity(taskId, task.getName(), task.getCategory().getId(), task.getCategory().getName(),
                        task.getCategory().getColor(), Action.STATUS_CHANGED, TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED,
                        startedAt, null));
        reopened.setOccurredAt(startedAt);
        return activity.save(reopened);
    }

    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/{taskId}/progress/{progressId}/delete")
    public Object deleteProgress(@PathVariable Long taskId, @PathVariable Long progressId,
                                 HttpServletRequest request, RedirectAttributes redirect) {
        boolean isAjax = request != null && ("XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))
                || (request.getHeader("Accept") != null && request.getHeader("Accept").contains("application/json")));
        progress.requireBelongsToTask(progressId, taskId);
        attachments.deleteAllForProgress(progressId);
        // The foreign key clears the note reference and keeps the milestone.
        progress.delete(taskId, progressId);
        if (isAjax) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Progress update deleted", "progressId", progressId));
        }
        redirect.addFlashAttribute("success", "Progress update deleted");
        return "redirect:/tasks/" + taskId;
    }

    @PostMapping("/{id}/attachments")
    public Object upload(@PathVariable Long id,
                         @RequestParam(required = false) List<MultipartFile> files,
                         @RequestParam(required = false) List<MultipartFile> file,
                         HttpServletRequest request,
                         RedirectAttributes redirect) {
        List<MultipartFile> allFiles = new java.util.ArrayList<>();
        if (request instanceof org.springframework.web.multipart.MultipartHttpServletRequest multipartRequest) {
            multipartRequest.getMultiFileMap().values().forEach(allFiles::addAll);
        } else {
            if (files != null) allFiles.addAll(files);
            if (file != null) allFiles.addAll(file);
        }
        boolean ajax = "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));
        int count = 0;
        try {
            for (MultipartFile f : allFiles) {
                if (f != null && !f.isEmpty()) {
                    attachments.store(id, f);
                    count++;
                }
            }
            if (ajax) {
                if (count == 0) return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Choose a file to upload"));
                return ResponseEntity.ok(Map.of("success", true));
            }
            if (count > 0) {
                redirect.addFlashAttribute("success", count == 1 ? "Attachment uploaded" : count + " attachments uploaded");
            }
        } catch (IOException | IllegalArgumentException ex) {
            String error = ex.getMessage() == null ? "Could not save the files." : ex.getMessage();
            if (ajax) {
                if (count > 0) error = count + " file(s) saved before the upload stopped. Check the task before retrying. " + error;
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", error));
            }
            redirect.addFlashAttribute("error", error);
        }
        return "redirect:/tasks/" + id;
    }

    @PostMapping("/{taskId}/progress/{progressId}/attachments")
    public String uploadProgressAttachment(@PathVariable Long taskId, @PathVariable Long progressId,
                                           @RequestParam(required = false) List<MultipartFile> files,
                                           @RequestParam(required = false) List<MultipartFile> file,
                                           HttpServletRequest request,
                                           RedirectAttributes redirect) {
        try {
            progress.requireBelongsToTask(progressId, taskId);
            List<MultipartFile> allFiles = new java.util.ArrayList<>();
            if (request instanceof org.springframework.web.multipart.MultipartHttpServletRequest multipartRequest) {
                multipartRequest.getMultiFileMap().values().forEach(allFiles::addAll);
            } else {
                if (files != null) allFiles.addAll(files);
                if (file != null) allFiles.addAll(file);
            }
            int count = 0;
            for (MultipartFile f : allFiles) {
                if (f != null && !f.isEmpty()) {
                    attachments.storeProgressAttachment(taskId, progressId, f);
                    count++;
                }
            }
            if (count > 0) {
                redirect.addFlashAttribute("success", count == 1 ? "Progress attachment uploaded" : count + " progress attachments uploaded");
            }
        } catch (IOException | IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/tasks/" + taskId;
    }

    @GetMapping("/{taskId}/attachments/{attachmentId}")
    public ResponseEntity<Resource> download(@PathVariable Long taskId, @PathVariable Long attachmentId,
                                             @RequestParam(defaultValue = "false") boolean preview) throws IOException {
        Attachment attachment = attachments.get(attachmentId);
        if (!attachment.getTaskId().equals(taskId)) throw new IllegalArgumentException("Attachment does not belong to this task");
        String safeFilename = SecurityUtils.sanitizeFilename(attachment.getOriginalName());
        String previewType = preview ? attachment.getPreviewContentType() : null;
        ContentDisposition.Builder disposition = previewType != null ? ContentDisposition.inline() : ContentDisposition.attachment();
        String responseType = previewType == null ? attachment.getContentType() : previewType;
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(responseType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.filename(safeFilename).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(attachments.load(attachment));
    }

    @PostMapping("/{taskId}/attachments/{attachmentId}/delete")
    public String deleteAttachment(@PathVariable Long taskId, @PathVariable Long attachmentId, RedirectAttributes redirect) {
        try { attachments.deleteFile(taskId, attachmentId); redirect.addFlashAttribute("success", "Attachment deleted"); }
        catch (IOException | IllegalArgumentException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/tasks/" + taskId;
    }

    @PostMapping("/{id}/links")
    public String addLink(@PathVariable Long id, @RequestParam String label, @RequestParam String url, RedirectAttributes redirect) {
        try { attachments.addLink(id, label, url); redirect.addFlashAttribute("success", "Reference link added"); }
        catch (IllegalArgumentException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/tasks/" + id;
    }

    @PostMapping("/{taskId}/links/{linkId}/delete")
    public String deleteLink(@PathVariable Long taskId, @PathVariable Long linkId, RedirectAttributes redirect) {
        try { attachments.deleteLink(taskId, linkId); redirect.addFlashAttribute("success", "Reference link deleted"); }
        catch (IllegalArgumentException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/tasks/" + taskId;
    }

    @PostMapping("/{id}/timer/start")
    @ResponseBody
    public ResponseEntity<?> startTimer(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirect) {
        try {
            Task task = tasks.startTimer(id);
            if (isJsonRequest(request)) {
                return ResponseEntity.ok(timerStatusPayload(task));
            }
            redirect.addFlashAttribute("success", "Timer started for " + task.getName());
        } catch (IllegalArgumentException ex) {
            if (isJsonRequest(request)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("success", false, "error", ex.getMessage()));
            }
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, refererOrDetail(request, id)).build();
    }

    @PostMapping("/{id}/timer/pause")
    @ResponseBody
    public ResponseEntity<?> pauseTimer(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirect) {
        Task task = tasks.pauseTimer(id);
        if (isJsonRequest(request)) {
            return ResponseEntity.ok(timerStatusPayload(task));
        }
        redirect.addFlashAttribute("success", "Timer paused for " + task.getName());
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, refererOrDetail(request, id)).build();
    }

    @PostMapping("/{id}/timer/stop")
    @ResponseBody
    public ResponseEntity<?> stopTimer(@PathVariable Long id,
                                       @RequestParam(required = false) String note,
                                       HttpServletRequest request, RedirectAttributes redirect) {
        Task task = tasks.stopTimer(id, note);
        if (isJsonRequest(request)) {
            return ResponseEntity.ok(timerStatusPayload(task));
        }
        redirect.addFlashAttribute("success", "Timer stopped for " + task.getName() + ". Total time: " + task.getFormattedTotalTime());
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, refererOrDetail(request, id)).build();
    }

    @GetMapping("/{id}/timer/status")
    @ResponseBody
    public ResponseEntity<?> timerStatus(@PathVariable Long id) {
        Task task = tasks.get(id);
        return ResponseEntity.ok(timerStatusPayload(task));
    }

    @GetMapping("/timer/active")
    @ResponseBody
    public ResponseEntity<?> activeTimer(@RequestParam(defaultValue = "false") boolean includeDayTotals) {
        var activeList = tasks.getActiveTimers();
        if (activeList.isEmpty()) {
            return ResponseEntity.ok(Map.of("hasActive", false, "count", 0, "activeTasks", List.of()));
        }
        LocalDate day = LocalDate.now();
        List<Map<String, Object>> taskPayloads = activeList.stream().map(task -> {
            var item = timerStatusPayload(task);
            if (includeDayTotals) item.put("todayBaseSeconds", tasks.timerDayBaseSeconds(task, day));
            return item;
        }).toList();
        Map<String, Object> payload = new LinkedHashMap<>(taskPayloads.get(0));
        if (includeDayTotals) {
            var zone = java.time.ZoneId.systemDefault();
            payload.put("timerDayStart", day.atStartOfDay(zone).toInstant().toEpochMilli());
            payload.put("timerNextDayStart", day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli());
        }
        payload.put("hasActive", true);
        payload.put("count", activeList.size());
        payload.put("activeTasks", taskPayloads);
        return ResponseEntity.ok(payload);
    }

    private boolean isJsonRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String requestedWith = request.getHeader("X-Requested-With");
        return (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE)) || "XMLHttpRequest".equalsIgnoreCase(requestedWith);
    }

    private String refererOrDetail(HttpServletRequest request, Long id) {
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank() && (referer.contains("/tasks") || referer.contains("/board") || referer.contains("/dashboard"))) {
            return referer;
        }
        return "/tasks/" + id;
    }

    private Map<String, Object> timerStatusPayload(Task task) {
        LocalDateTime now = LocalDateTime.now();
        long activeSession = task.getActiveSessionSeconds(now);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", true);
        map.put("taskId", task.getId());
        map.put("taskName", task.getName());
        map.put("timerStatus", task.getTimerStatus().name());
        map.put("timerStartedAt", task.getTimerStartedAt() != null ? task.getTimerStartedAt().toString() : null);
        map.put("timerStartedAtMillis", task.getTimerStartedAtMillis());
        map.put("timerAccumulatedSeconds", task.getTimerAccumulatedSeconds());
        map.put("activeSessionSeconds", activeSession);
        map.put("totalTimeSpentSeconds", task.getTotalTimeSpentSeconds());
        map.put("effectiveTotalSeconds", task.getEffectiveTotalSeconds(now));
        map.put("formattedTotalTime", task.getFormattedTotalTime());
        map.put("formattedCompactTime", task.getFormattedCompactTime());
        map.put("formattedActiveSession", Task.formatSeconds(activeSession));
        return map;
    }

    private void prepareForm(Model model, String title) {
        model.addAttribute("pageTitle", title); model.addAttribute("categories", categories.findActive());
        model.addAttribute("statuses", TaskStatus.values()); model.addAttribute("priorities", Priority.values());
        TaskForm form = (TaskForm) model.getAttribute("taskForm");
        Long id = form.getId();
        model.addAttribute("savedFiles", id == null ? List.of() : attachments.filesForTask(id));
        model.addAttribute("savedLinks", id == null ? List.of() : attachments.linksForTask(id));
        if (!model.containsAttribute("removeFileIds")) model.addAttribute("removeFileIds", List.of());
        if (!model.containsAttribute("removeLinkIds")) model.addAttribute("removeLinkIds", List.of());
        if (!model.containsAttribute("taskLinkInputs")) {
            model.addAttribute("taskLinkInputs", List.of(new TaskLinkInput("", "")));
        }
    }
}
