package com.productivity.app;

import com.productivity.category.entity.Category;
import com.productivity.category.repository.CategoryRepository;
import com.productivity.attachment.repository.AttachmentRepository;
import com.productivity.attachment.repository.ReferenceLinkRepository;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.storage.default-location=",
        "spring.datasource.url=jdbc:h2:mem:productivity-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.seed-demo-data=false",
        "app.storage.root=target/test-storage",
        "app.security.csrf.enabled=false",
        "app.security.rate-limit.enabled=false"
})
class ProductivityApplicationTest {
    @Autowired MockMvc mvc;
    @Autowired CategoryRepository categories;
    @Autowired TaskRepository tasks;
    @Autowired TaskActivityRepository activity;
    @Autowired ProgressUpdateRepository updates;
    @Autowired AttachmentRepository attachments;
    @Autowired ReferenceLinkRepository links;
    @Autowired TaskTimeLogRepository timeLogs;
    @Autowired TaskIterationRepository iterations;
    @Autowired JdbcTemplate jdbc;
    @Autowired(required = false) org.springframework.context.ApplicationContext applicationContext;

    private boolean hasBoard() {
        return applicationContext != null && applicationContext.containsBean("boardController");
    }

    private Category category;

    @BeforeEach
    void setup() {
        jdbc.update("delete from deleted_tasks");
        attachments.deleteAll();
        links.deleteAll();
        updates.deleteAll();
        timeLogs.deleteAll();
        tasks.deleteAll();
        activity.deleteAllInBatch();
        categories.deleteAll();
        category = new Category();
        category.setName("Work");
        category.setColor("#2563EB");
        category = categories.save(category);
    }

    @Test
    void dashboardAndMainViewsRender() throws Exception {
        createTask("Prepare test report", TaskStatus.IN_PROGRESS);

        mvc.perform(get("/dashboard")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Prepare test report")));
        if (hasBoard()) {
            mvc.perform(get("/board")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Daily board")));
        }
        mvc.perform(get("/analytics")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Tasks by Category")));
        mvc.perform(get("/overdue")).andExpect(status().isOk());
        mvc.perform(get("/history")).andExpect(status().isOk());
    }

    @Test
    void dashboardRedesignComponentsRender() throws Exception {
        createTask("Complete quarterly audit", TaskStatus.IN_PROGRESS);

        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("topbar-search")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("⌘ K")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("user-avatar")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("greeting-banner")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Aswin!")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("landscape-svg")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/images/dashboard-banner.svg")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/images/dashboard-banner-dark.svg")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("week-strip-card")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("week-strip-following")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("is-today")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tasks today")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("In progress")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Completed")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Overdue")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Today’s work")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("work-filter-tabs")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("task-check-btn")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Complete quarterly audit")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Completion rate")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("overdue-empty-illustration")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No tasks are overdue.")));
    }

    @Test
    void dashboardDateNavigationAndCalendarArrows() throws Exception {
        LocalDate customDate = LocalDate.now().plusDays(4);
        Task futureTask = new Task();
        futureTask.setName("Future milestone review");
        futureTask.setDescription("Review milestone roadmap for selected date");
        futureTask.setCategory(category);
        futureTask.setPlannedDate(customDate);
        futureTask.setDeadline(customDate.plusDays(1));
        futureTask.setPriority(Priority.HIGH);
        futureTask.setStatus(TaskStatus.IN_PROGRESS);
        tasks.save(futureTask);

        // 1. Verify default /dashboard has calendar arrows and date links pointing to /dashboard, NOT /board
        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/dashboard?date=")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("week-nav-btn\" href=\"/board"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("week-day-number\" href=\"/board"))));

        // 2. Navigate to /dashboard for custom date
        mvc.perform(get("/dashboard").param("date", customDate.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("is-selected")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Future milestone review")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/dashboard?date=")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Today")));
    }

    @Test
    void themeToggleAndScriptArePresentInLayout() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-theme-toggle")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("focus-theme")));

        mvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Color Theme")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-theme-set=\"dark\"")));
    }

    @Test
    void viewsIncludeModularCssAndJs() throws Exception {
        if (hasBoard()) {
            mvc.perform(get("/board"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("board.css")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("board.js")));
        }

        mvc.perform(get("/analytics"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("analytics.css")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("analytics.js")));

        mvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("categories.css")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("categories.js")));

        mvc.perform(get("/history"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("history.css")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("history.js")));

        mvc.perform(get("/overdue"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("overdue.css")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("overdue.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Overdue Tasks")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("overdue-banner"))));
    }

    @Test
    void boardRedesignComponentsRender() throws Exception {
        if (!hasBoard()) return;
        createTask("Architecture task", TaskStatus.YET_TO_START);
        createTask("Feature task", TaskStatus.IN_PROGRESS);
        createTask("Release task", TaskStatus.COMPLETED);

        mvc.perform(get("/board"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("board-banner"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Daily board")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("board-stats-card")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Total tasks")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Completed")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("donut-chart")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("board-controls-toolbar")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("category-select")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("date-strip-nav")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-label=\"Board date\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("kanban")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("column-yet-to-start")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("column-in-progress")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("column-completed")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Architecture task")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Feature task")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Release task")));
    }

    @Test
    void notFoundReturnsCustom404PageForMissingUrl() throws Exception {
        mvc.perform(get("/non-existent-endpoint-xyz"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("404 · Not Found")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Page or Record Not Found")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Go to Dashboard")));
    }

    @Test
    void notFoundReturnsCustom404PageForMissingTask() throws Exception {
        mvc.perform(get("/tasks/999999"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("404 · Not Found")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Task not found")));
    }

    @Test
    void newTaskUsesHtmlCompatibleIsoDate() throws Exception {
        mvc.perform(get("/tasks/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.stringContainsInOrder(
                        "task-editor-create", "Task details", "Supporting Materials", "taskEditorDropzone", "taskEditorAddLink")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "value=\"" + LocalDate.now() + "\"")));
    }

    @Test
    void taskLifecyclePersistsAndRedirectsToDetails() throws Exception {
        mvc.perform(post("/tasks/save")
                        .param("name", "Ship release notes")
                        .param("description", "Document the changes included in the release.")
                        .param("plannedDate", LocalDate.now().toString())
                        .param("deadline", LocalDate.now().plusDays(1).toString())
                        .param("status", "YET_TO_START")
                        .param("priority", "HIGH")
                        .param("categoryId", category.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/tasks/*"));

        Task saved = tasks.findAll().get(0);
        assertThat(saved.getName()).isEqualTo("Ship release notes");

        mvc.perform(post("/tasks/{id}/status", saved.getId()).param("status", "COMPLETED").param("completionNote", "Published with the release."))
                .andExpect(status().is3xxRedirection());

        Task completed = tasks.findById(saved.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();

        assertThat(completed.getCompletionNote()).isEqualTo("Published with the release.");

        mvc.perform(get("/tasks/{id}/edit", completed.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.stringContainsInOrder(
                        "task-editor-hero", "Edit task", "Task details", "Ship release notes", "Published with the release.",
                        "Supporting Materials", "Save changes")));

        mvc.perform(get("/tasks/{id}", completed.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("task-detail.css")))
                .andExpect(content().string(org.hamcrest.Matchers.stringContainsInOrder(
                        "task-detail-toolbar", "task-detail-breadcrumb", "Add progress update",
                        "Progress timeline", "Marked as completed", "Task created")));
    }

    @Test
    void invalidTaskReturnsFormWithUsefulValidation() throws Exception {
        mvc.perform(post("/tasks/save").param("name", "").param("status", "YET_TO_START").param("priority", "MEDIUM"))
                .andExpect(status().isOk())
                .andExpect(view().name("tasks/form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Enter a task name")));
    }

    @Test
    void analyticsCustomPeriodUsesPlannedDates() throws Exception {
        LocalDate today = LocalDate.now();
        Task completed = createTask("Complete report", TaskStatus.COMPLETED);
        completed.setCompletedAt(today.atStartOfDay());
        tasks.save(completed);
        Task earlier = createTask("Earlier planned work", TaskStatus.YET_TO_START);
        earlier.setPlannedDate(today.minusDays(1));
        tasks.save(earlier);

        mvc.perform(get("/analytics").param("period", "custom")
                        .param("from", today.toString()).param("to", today.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1 of 1 planned tasks are complete (100%).")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("100% (1)")));

        mvc.perform(get("/analytics").param("period", "custom")
                        .param("from", today.toString()).param("to", today.minusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Choose a start and end date in order")));
    }

    @Test
    void csvExportsIncludeTaskDetailsAndKeepReportFilters() throws Exception {
        Task task = createTask("Close monthly accounts", TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        task.setDescription("Reconcile the ledger");
        addTimerAndIterationHistory(task);
        Task open = createTask("Reconcile next month", TaskStatus.IN_PROGRESS);
        open.setArchived(true);
        tasks.save(open);

        var update = new ProgressUpdate();
        update.setTask(task);
        update.setDescription("Reconciled the ledger");
        update.setReminder("Review the statement");
        update = updates.save(update);
        var event = new TaskActivity(task.getId(), task.getName(), category.getId(), category.getName(),
                category.getColor(), TaskActivity.Action.COMPLETED, TaskStatus.COMPLETED, TaskStatus.IN_PROGRESS,
                task.getCompletedAt(), "Ledger closed");
        event.setProgressUpdateId(update.getId());
        activity.save(event);

        mvc.perform(multipart("/tasks/{taskId}/progress/{progressId}/attachments", task.getId(), update.getId())
                        .file(new MockMultipartFile("file", "statement.txt", "text/plain", "file contents".getBytes())))
                .andExpect(status().is3xxRedirection());
        var link = new com.productivity.attachment.entity.ReferenceLink();
        link.setTaskId(task.getId());
        link.setProgressUpdateId(update.getId());
        link.setLabel("Statement reference");
        link.setUrl("https://example.com/statement");
        links.save(link);

        var details = org.hamcrest.Matchers.stringContainsInOrder("Progress updates,Timeline events,Iteration history,Time logs,Reference links,Attachments",
                "Close monthly accounts", "Reconcile the ledger", "Reconciled the ledger", "Review the statement",
                "Ledger closed", "First iteration", "Split session", "https://example.com/statement", "statement.txt");

        mvc.perform(get("/tasks/{id}/export", task.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(details));
        mvc.perform(get("/settings/data/tasks.csv"))
                .andExpect(status().isOk())
                .andExpect(content().string(details))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(open.getName())));
        mvc.perform(get("/analytics/export").param("query", "Close monthly"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
                .andExpect(content().string(details))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(open.getName()))));

        try (var book = excelExport("/settings/data/tasks.xlsx")) {
            var sheet = book.getSheet("Tasks");
            var first = sheet.getRow(3);
            assertThat(first.getCell(0).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(task.getPlannedDate());
            assertThat(first.getCell(0).getCellStyle().getDataFormatString()).isEqualTo("dd mmm yyyy");
            assertThat(first.getCell(6).getNumericCellValue()).isCloseTo(3600.0 / 86_400,
                    org.assertj.core.data.Offset.offset(1e-10));
            assertThat(first.getCell(7).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(task.getCompletedAt().toLocalDate());
            assertThat(sheet.getRow(4).getCell(1).getStringCellValue()).isEqualTo(open.getName());
            assertThat(sheet.getRow(4).getCell(7).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
            assertThat(sheet.getDataValidations()).hasSize(2);
            assertThat(sheet.getDataValidations().get(0).getValidationConstraint().getExplicitListValues())
                    .containsExactly("Yet to start", "In progress", "Completed", "Blocked/Closed");
            assertThat(sheet.getDataValidations().get(1).getValidationConstraint().getExplicitListValues())
                    .containsExactly("Low", "Medium", "High");
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            assertThat(sheet.getSheetConditionalFormatting().getNumConditionalFormattings()).isEqualTo(5);
            assertThat(sheet.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A3:W5");
            assertThat(book.getSheet("Progress Updates").getRow(3).getCell(5).getStringCellValue()).isEqualTo("Reconciled the ledger");
            assertThat(book.getSheet("Timeline").getRow(3).getCell(7).getStringCellValue()).isEqualTo("Ledger closed");
            assertThat(book.getSheet("Iterations").getRow(3).getCell(5).getStringCellValue()).isEqualTo("First iteration");
            assertThat(book.getSheet("Time Logs").getRow(3).getCell(6).getStringCellValue()).isEqualTo("Split session");
            assertThat(book.getSheet("Daily Time").getLastRowNum()).isEqualTo(5);
            assertThat(book.getSheet("Reference Links").getRow(3).getCell(5).getHyperlink().getAddress())
                    .isEqualTo("https://example.com/statement");
            assertThat(book.getSheet("Attachments").getRow(3).getCell(4).getStringCellValue()).isEqualTo("statement.txt");
        }
        try (var book = excelExport("/tasks/" + task.getId() + "/export.xlsx")) {
            assertThat(book.getSheet("Tasks").getLastRowNum()).isEqualTo(3);
        }
        try (var book = excelExport("/analytics/export.xlsx?query=Close%20monthly")) {
            assertThat(book.getSheet("Tasks").getLastRowNum()).isEqualTo(3);
            assertThat(book.getSheet("Tasks").getRow(3).getCell(1).getStringCellValue()).isEqualTo(task.getName());
        }
    }

    private org.apache.poi.xssf.usermodel.XSSFWorkbook excelExport(String path) throws Exception {
        byte[] content = mvc.perform(get(java.net.URI.create(path)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.endsWith(".xlsx\"")))
                .andReturn().getResponse().getContentAsByteArray();
        return new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(content));
    }

    @Test
    void historyFiltersByCompletionDate() throws Exception {
        Task old = createTask("Older completed work", TaskStatus.COMPLETED);
        old.setCompletedAt(java.time.LocalDateTime.now().minusDays(10));
        tasks.save(old);
        Task recent = createTask("Recent completed work", TaskStatus.COMPLETED);
        recent.setCompletedAt(java.time.LocalDateTime.now());
        tasks.save(recent);
        for (Task task : java.util.List.of(old, recent)) {
            activity.save(new TaskActivity(task.getId(), task.getName(), category.getId(), category.getName(),
                    category.getColor(), TaskActivity.Action.COMPLETED, task.getStatus(), TaskStatus.IN_PROGRESS,
                    task.getCompletedAt(), task.getCompletionNote()));
        }

        mvc.perform(get("/history").param("action", "COMPLETED").param("from", LocalDate.now().minusDays(1).toString()).param("to", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Recent completed work")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Older completed work"))));
    }

    @Test
    void overdueViewExcludesCompletedTasksAndTasksWithoutDeadlines() throws Exception {
        Task overdue = createTask("Actually overdue", TaskStatus.IN_PROGRESS);
        overdue.setDeadline(LocalDate.now().minusDays(3));
        tasks.save(overdue);
        Task completed = createTask("Completed on time", TaskStatus.COMPLETED);
        completed.setDeadline(LocalDate.now().minusDays(3)); completed.setCompletedAt(java.time.LocalDateTime.now());
        tasks.save(completed);
        Task undated = createTask("No deadline", TaskStatus.IN_PROGRESS);
        undated.setDeadline(null); tasks.save(undated);

        mvc.perform(get("/overdue"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Actually overdue")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Completed on time"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("No deadline"))));
    }

    @Test
    void taskAndProgressAttachmentsCanBeUploadedAndDownloaded() throws Exception {
        Task task = createTask("Documented work", TaskStatus.IN_PROGRESS);
        ProgressUpdate update = new ProgressUpdate();
        update.setTask(task); update.setDescription("Prepared the first draft");
        update = updates.save(update);
        MockMultipartFile taskFile = new MockMultipartFile("file", "brief.txt", "text/plain", "task brief".getBytes());
        MockMultipartFile progressFile = new MockMultipartFile("file", "notes.txt", "text/plain", "progress notes".getBytes());

        mvc.perform(multipart("/tasks/{id}/attachments", task.getId()).file(taskFile)).andExpect(status().is3xxRedirection());
        mvc.perform(multipart("/tasks/{taskId}/progress/{progressId}/attachments", task.getId(), update.getId()).file(progressFile))
                .andExpect(status().is3xxRedirection());

        assertThat(attachments.findByTaskIdAndProgressUpdateIdIsNullOrderByCreatedAtDesc(task.getId())).hasSize(1);
        assertThat(attachments.findByProgressUpdateIdOrderByCreatedAtDesc(update.getId())).hasSize(1);
        var saved = attachments.findByProgressUpdateIdOrderByCreatedAtDesc(update.getId()).get(0);
        mvc.perform(get("/tasks/{taskId}/attachments/{attachmentId}", task.getId(), saved.getId()))
                .andExpect(status().isOk()).andExpect(content().bytes("progress notes".getBytes()));
        mvc.perform(get("/tasks/{taskId}/attachments/{attachmentId}", task.getId(), saved.getId()).param("preview", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")))
                .andExpect(content().contentType("text/plain"));
        mvc.perform(get("/tasks/{id}", task.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Preview notes.txt in a new tab")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Preview brief.txt in a new tab")));
    }

    @Test
    void attachmentUploadPopupReceivesSuccessAndFailureResponses() throws Exception {
        Task task = createTask("Upload files", TaskStatus.IN_PROGRESS);
        mvc.perform(get("/tasks/{id}", task.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<dialog id=\"uploadNotice\"")));

        var file = new MockMultipartFile("file", "notes.txt", "text/plain", "upload progress".getBytes());
        mvc.perform(multipart("/tasks/{id}/attachments", task.getId()).file(file)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mvc.perform(multipart("/tasks/{id}/attachments", task.getId())
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Choose a file to upload"));
    }

    @Test
    void selectedFocusFolderStoresProgressMaterialsAndReconcilesDeletedFiles(@TempDir Path directory) throws Exception {
        Path config = Path.of("target/test-storage/focus-location/location.txt");
        try {
            mvc.perform(post("/settings/storage").param("folder", directory.toString()))
                    .andExpect(redirectedUrl("/settings"))
                    .andExpect(flash().attribute("success", "Focus storage folder saved."));
            mvc.perform(get("/settings")).andExpect(model().attribute("focusLocation", directory.toRealPath().toString()));

            Task task = createTask("Review notes", TaskStatus.IN_PROGRESS);
            MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "attachment content".getBytes());
            mvc.perform(multipart("/tasks/{id}/progress", task.getId()).file(file)
                            .param("description", "Reviewed the first draft")
                            .param("linkUrl", "https://example.com/review"))
                    .andExpect(status().is3xxRedirection());

            Path taskFolder = directory.resolve("focus/Review notes [" + task.getId() + "]");
            Path updateFolder;
            try (var folders = Files.list(taskFolder)) {
                updateFolder = folders.filter(folder -> folder.getFileName().toString().startsWith("Progress update - "))
                        .findFirst().orElseThrow();
            }
            assertThat(Files.readString(updateFolder.resolve("message.txt"))).contains("Reviewed the first draft");
            assertThat(Files.readString(taskFolder.resolve("timeline.txt"))).contains("Task created", "Reviewed the first draft");
            Path linkFile = updateFolder.resolve("links.txt");
            assertThat(Files.readString(linkFile)).contains("https://example.com/review");

            var attachment = attachments.findByTaskIdOrderByCreatedAtDesc(task.getId()).get(0);
            Path attachmentFile = updateFolder.resolve("attachments/" + attachment.getId() + " - notes.txt");
            assertThat(Files.readString(attachmentFile)).isEqualTo("attachment content");
            Files.delete(attachmentFile);
            Files.delete(linkFile);

            mvc.perform(get("/tasks/{id}/storage-materials", task.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.files").isEmpty())
                    .andExpect(jsonPath("$.links").isEmpty());
            mvc.perform(get("/tasks/{id}", task.getId()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("notes.txt"))));
            assertThat(attachments.findById(attachment.getId())).isEmpty();
            assertThat(links.findByTaskIdOrderByCreatedAtDesc(task.getId())).isEmpty();
            assertThat(Files.exists(attachmentFile)).isFalse();
            assertThat(Files.exists(linkFile)).isFalse();

            mvc.perform(post("/tasks/{id}/status", task.getId())
                            .param("status", "COMPLETED").param("completionNote", "Review delivered"))
                    .andExpect(status().is3xxRedirection());
            assertThat(Files.readString(taskFolder.resolve("timeline.txt")))
                    .contains("Task created", "Reviewed the first draft", "Review delivered");
        } finally {
            Files.deleteIfExists(config);
        }
    }

    @Test
    void fullBackupRestoresRecordsAndFiles() throws Exception {
        Task task = createTask("Backed up task", TaskStatus.IN_PROGRESS);
        addTimerAndIterationHistory(task);
        MockMultipartFile taskFile = new MockMultipartFile("file", "backup-note.txt", "text/plain", "keep me".getBytes());
        mvc.perform(multipart("/tasks/{id}/attachments", task.getId()).file(taskFile)).andExpect(status().is3xxRedirection());

        MvcResult export = mvc.perform(get("/settings/data/backup"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "application/zip"))
                .andReturn();
        MockMultipartFile archive = new MockMultipartFile("backup", "focus-backup.zip", "application/zip", export.getResponse().getContentAsByteArray());

        mvc.perform(multipart("/settings/data/restore").file(archive)).andExpect(status().is3xxRedirection());

        Task restored = tasks.findAll().stream().filter(item -> item.getName().equals("Backed up task")).findFirst().orElseThrow();
        assertTimerAndIterationHistory(restored);
        assertThat(attachments.findByTaskIdOrderByCreatedAtDesc(restored.getId())).hasSize(1);
        var restoredFile = attachments.findByTaskIdOrderByCreatedAtDesc(restored.getId()).get(0);
        mvc.perform(get("/tasks/{taskId}/attachments/{attachmentId}", restored.getId(), restoredFile.getId()))
                .andExpect(status().isOk()).andExpect(content().bytes("keep me".getBytes()));
    }

    @Test
    void restoresVersionOneBackupWithDefaultTimerState() throws Exception {
        byte[] manifest = """
                {"version":1,"categories":[{"id":1,"name":"Legacy","color":"#2563EB","archived":false}],
                 "tasks":[{"id":1,"categoryId":1,"name":"Legacy task","plannedDate":"2026-06-30",
                           "status":"IN_PROGRESS","priority":"MEDIUM","iteration":1,"archived":false}],
                 "progressUpdates":[],"attachments":[],"links":[]}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var bytes = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("focus-backup.json"));
            zip.write(manifest);
            zip.closeEntry();
        }
        var archive = new MockMultipartFile("backup", "legacy.zip", "application/zip", bytes.toByteArray());

        mvc.perform(multipart("/settings/data/restore").file(archive))
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attributeExists("success"));

        assertThat(tasks.findAll()).singleElement().satisfies(task -> {
            assertThat(task.getName()).isEqualTo("Legacy task");
            assertThat(task.getTimerStatus()).isEqualTo(TaskTimerStatus.STOPPED);
            assertThat(task.getTotalTimeSpentSeconds()).isZero();
        });
    }

    @Test
    void backupRoundTripsAnAttachmentLargerThanTheFormerRestoreLimit() throws Exception {
        Task task = createTask("Large backup attachment", TaskStatus.IN_PROGRESS);
        var file = new MockMultipartFile("file", "large.bin", "application/octet-stream", new byte[]{1});
        mvc.perform(multipart("/tasks/{id}/attachments", task.getId()).file(file)).andExpect(status().is3xxRedirection());

        var original = attachments.findByTaskIdOrderByCreatedAtDesc(task.getId()).get(0);
        Path attachmentDir = Path.of("target/test-storage/task-attachments");
        Path originalPath = attachmentDir.resolve(original.getStoredName());
        long size = 51L * 1024 * 1024;
        try (var contents = new java.io.RandomAccessFile(originalPath.toFile(), "rw")) {
            contents.setLength(size);
        }
        original.setSizeBytes(size);
        attachments.save(original);

        try {
            var response = mvc.perform(get("/settings/data/backup")).andExpect(status().isOk()).andReturn().getResponse();
            var archive = new MockMultipartFile("backup", "focus-backup.zip", "application/zip", response.getContentAsByteArray());
            mvc.perform(multipart("/settings/data/restore").file(archive))
                    .andExpect(flash().attributeExists("success"));
            var restored = attachments.findAll().get(0);
            assertThat(restored.getId()).isNotEqualTo(original.getId());
            assertThat(Files.size(attachmentDir.resolve(restored.getStoredName()))).isEqualTo(size);
        } finally {
            for (var attachment : attachments.findAll()) {
                Files.deleteIfExists(attachmentDir.resolve(attachment.getStoredName()));
            }
            Files.deleteIfExists(originalPath);
        }
    }

    @Test
    void categoryCreateAndEditLifecycle() throws Exception {
        mvc.perform(post("/categories")
                        .param("name", "Productivity")
                        .param("color", "#10B981"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/categories"));

        Category saved = categories.findByNameIgnoreCase("Productivity").orElseThrow();
        assertThat(saved.getColor()).isEqualTo("#10B981");

        mvc.perform(get("/categories").param("edit", saved.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Productivity")));

        mvc.perform(post("/categories")
                        .param("id", saved.getId().toString())
                        .param("name", "Deep Work")
                        .param("color", "#6366F1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/categories"));

        Category updated = categories.findById(saved.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Deep Work");
        assertThat(updated.getColor()).isEqualTo("#6366F1");
    }

    @Test
    void taskSearchWithStatusAndPriorityFilters() throws Exception {
        Task t1 = createTask("Important In-Progress Task", TaskStatus.IN_PROGRESS);
        t1.setPriority(Priority.HIGH);
        tasks.save(t1);

        Task t2 = createTask("Low Priority Completed Task", TaskStatus.COMPLETED);
        t2.setPriority(Priority.LOW);
        tasks.save(t2);

        mvc.perform(get("/tasks")
                        .param("status", "IN_PROGRESS")
                        .param("priority", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Important In-Progress Task")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Low Priority Completed Task"))));
    }

    @Test
    void taskListPaginatesSortedResultsAndPreservesFilters() throws Exception {
        for (int day = 1; day <= 7; day++) {
            Task task = createTask("Pagination fixture " + day, TaskStatus.IN_PROGRESS);
            task.setDeadline(LocalDate.of(2030, 1, day));
            tasks.save(task);
        }

        mvc.perform(get("/tasks")
                        .param("query", "Pagination fixture")
                        .param("status", "IN_PROGRESS")
                        .param("sort", "due-desc")
                        .param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Showing 7–7 of 7 tasks")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Pagination fixture 1")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Pagination fixture 7"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("sort=due-desc&amp;page=1")));
    }

    @Test
    void taskCreationWithInitialAttachmentAndLink() throws Exception {
        MockMultipartFile file = new MockMultipartFile("attachment", "spec.txt", "text/plain", "specification".getBytes());

        mvc.perform(multipart("/tasks/save")
                        .file(file)
                        .param("name", "Task With Supporting Materials")
                        .param("plannedDate", LocalDate.now().toString())
                        .param("status", "YET_TO_START")
                        .param("priority", "HIGH")
                        .param("categoryId", category.getId().toString())
                        .param("linkLabel", "Antigravity")
                        .param("linkUrl", "https://antigravity.google.com"))
                .andExpect(status().is3xxRedirection());

        Task created = tasks.findAll().stream().filter(t -> t.getName().equals("Task With Supporting Materials")).findFirst().orElseThrow();
        assertThat(attachments.findByTaskIdAndProgressUpdateIdIsNullOrderByCreatedAtDesc(created.getId())).hasSize(1);
        assertThat(links.findByTaskIdOrderByCreatedAtDesc(created.getId())).hasSize(1);
    }

    @Test
    void progressDeletionCleansUpAttachments() throws Exception {
        Task task = createTask("Task with progress to delete", TaskStatus.IN_PROGRESS);
        ProgressUpdate update = new ProgressUpdate();
        update.setTask(task);
        update.setDescription("Initial draft");
        update = updates.save(update);

        MockMultipartFile file = new MockMultipartFile("file", "progress.txt", "text/plain", "draft".getBytes());
        mvc.perform(multipart("/tasks/{taskId}/progress/{progressId}/attachments", task.getId(), update.getId()).file(file))
                .andExpect(status().is3xxRedirection());

        assertThat(attachments.findByProgressUpdateIdOrderByCreatedAtDesc(update.getId())).hasSize(1);

        mvc.perform(post("/tasks/{taskId}/progress/{progressId}/delete", task.getId(), update.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tasks/" + task.getId()));

        assertThat(updates.findById(update.getId())).isEmpty();
        assertThat(attachments.findByProgressUpdateIdOrderByCreatedAtDesc(update.getId())).isEmpty();
    }

    @Test
    void taskDeletionCleansUpAttachmentsAndRecords() throws Exception {
        Task task = createTask("Task to delete completely", TaskStatus.IN_PROGRESS);
        MockMultipartFile file = new MockMultipartFile("file", "delete-me.txt", "text/plain", "delete content".getBytes());
        mvc.perform(multipart("/tasks/{id}/attachments", task.getId()).file(file)).andExpect(status().is3xxRedirection());

        assertThat(attachments.findByTaskIdOrderByCreatedAtDesc(task.getId())).hasSize(1);

        mvc.perform(post("/tasks/{id}/delete", task.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tasks"))
                .andExpect(flash().attribute("deletedTaskId", task.getId()))
                .andExpect(flash().attribute("deletedTaskName", "Task to delete completely"));

        assertThat(tasks.findById(task.getId())).isEmpty();
        assertThat(attachments.findByTaskIdOrderByCreatedAtDesc(task.getId())).isEmpty();
    }

    @Test
    void taskDeletionSupportsUndoAndRestoresTaskHistoryAndFiles() throws Exception {
        Task task = createTask("Important Task To Undo", TaskStatus.IN_PROGRESS);
        task.setDescription("Must not be lost accidentally");
        addTimerAndIterationHistory(task);
        mvc.perform(multipart("/tasks/{id}/progress", task.getId())
                        .file(new MockMultipartFile("files", "progress.txt", "text/plain", "retained progress".getBytes()))
                        .param("description", "Progress to restore")
                        .param("linkUrls", "https://example.com/progress"))
                .andExpect(status().is3xxRedirection());

        // Add a reference link to the task
        com.productivity.attachment.entity.ReferenceLink link = new com.productivity.attachment.entity.ReferenceLink();
        link.setTaskId(task.getId());
        link.setUrl("https://github.com/example/issue/99");
        link.setLabel("Issue #99");
        links.save(link);

        Long taskId = task.getId();

        // 1. Delete task via standard form submission
        mvc.perform(post("/tasks/{id}/delete", taskId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tasks"))
                .andExpect(flash().attribute("deletedTaskId", taskId))
                .andExpect(flash().attribute("deletedTaskName", "Important Task To Undo"));

        assertThat(tasks.findById(taskId)).isEmpty();

        mvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Deleted tasks (1)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/tasks/" + taskId + "/undo-delete")));

        // 2. Undo delete
        MvcResult result = mvc.perform(post("/tasks/{id}/undo-delete", taskId))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("info", "Task \"Important Task To Undo\" restored."))
                .andReturn();

        String redirectTarget = result.getResponse().getRedirectedUrl();
        Long restoredId = Long.valueOf(redirectTarget.substring(redirectTarget.lastIndexOf('/') + 1));

        // 3. Verify task is restored with properties intact
        Task restored = tasks.findById(restoredId).orElseThrow();
        assertThat(restored.getName()).isEqualTo("Important Task To Undo");
        assertThat(restored.getDescription()).isEqualTo("Must not be lost accidentally");
        assertThat(restored.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

        assertTimerAndIterationHistory(restored);
        var restoredLinks = links.findByTaskIdAndProgressUpdateIdIsNullOrderByCreatedAtDesc(restoredId);
        assertThat(restoredLinks).singleElement().satisfies(item -> {
            assertThat(item.getUrl()).isEqualTo("https://github.com/example/issue/99");
            assertThat(item.getLabel()).isEqualTo("Issue #99");
        });
        var restoredProgress = updates.findByTaskIdOrderByRecordedAtDesc(restoredId).get(0);
        assertThat(restoredProgress.getDescription()).isEqualTo("Progress to restore");
        assertThat(links.findByProgressUpdateIdOrderByCreatedAtDesc(restoredProgress.getId()))
                .singleElement().satisfies(item -> assertThat(item.getUrl()).isEqualTo("https://example.com/progress"));
        var restoredFile = attachments.findByProgressUpdateIdOrderByCreatedAtDesc(restoredProgress.getId()).get(0);
        mvc.perform(get("/tasks/{taskId}/attachments/{attachmentId}", restoredId, restoredFile.getId()))
                .andExpect(status().isOk()).andExpect(content().bytes("retained progress".getBytes()));
        mvc.perform(post("/tasks/{id}/undo-delete", taskId).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isBadRequest());
    }

    private void addTimerAndIterationHistory(Task task) {
        LocalDate day = LocalDate.of(2026, 6, 30);
        var started = day.atTime(23, 30);
        task.setBoardPosition(4);
        task.setIteration(2);
        task.setTotalTimeSpentSeconds(3600);
        task.setTimerStatus(TaskTimerStatus.PAUSED);
        task.setTimerAccumulatedSeconds(60);
        task.setTimerSessionStartedAt(day.atTime(18, 0));
        task.setTimerDailySeconds(Map.of(day, 60L));
        tasks.save(task);

        TaskIteration iteration = new TaskIteration();
        iteration.setTask(task);
        iteration.setStartedAt(started);
        iteration.setCompletedAt(started.plusHours(1));
        iteration.setCompletionNote("First iteration");
        iterations.save(iteration);

        TaskTimeLog log = new TaskTimeLog(task, started, started.plusHours(1), 3600, "Split session");
        log.setDailySeconds(Map.of(day, 2400L, day.plusDays(1), 1200L));
        timeLogs.save(log);
    }

    private void assertTimerAndIterationHistory(Task task) {
        LocalDate day = LocalDate.of(2026, 6, 30);
        assertThat(task.getBoardPosition()).isEqualTo(4);
        assertThat(task.getIteration()).isEqualTo(2);
        assertThat(task.getTotalTimeSpentSeconds()).isEqualTo(3600);
        assertThat(task.getTimerStatus()).isEqualTo(TaskTimerStatus.PAUSED);
        assertThat(task.getTimerAccumulatedSeconds()).isEqualTo(60);
        assertThat(task.getTimerSessionStartedAt()).isEqualTo(day.atTime(18, 0));
        assertThat(task.getTimerDailySeconds()).containsExactlyEntriesOf(Map.of(day, 60L));
        assertThat(iterations.findByTaskIdOrderByIterationNumberAsc(task.getId())).singleElement()
                .satisfies(item -> assertThat(item.getCompletionNote()).isEqualTo("First iteration"));
        assertThat(timeLogs.findByTaskIdOrderByStartedAtDesc(task.getId())).singleElement().satisfies(item -> {
            assertThat(item.getNote()).isEqualTo("Split session");
            assertThat(item.secondsOn(day)).isEqualTo(2400);
            assertThat(item.secondsOn(day.plusDays(1))).isEqualTo(1200);
        });
    }

    @Test
    void taskDeletionViaAjaxReturnsUndoJsonAndCanBeUndone() throws Exception {
        Task task = createTask("Ajax Deletion Task", TaskStatus.YET_TO_START);
        Long taskId = task.getId();

        // 1. Delete via AJAX
        mvc.perform(post("/tasks/{id}/delete", taskId)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.taskId").value(taskId))
                .andExpect(jsonPath("$.taskName").value("Ajax Deletion Task"))
                .andExpect(jsonPath("$.undoUrl").value("/tasks/" + taskId + "/undo-delete"));

        assertThat(tasks.findById(taskId)).isEmpty();

        // 2. Undo via AJAX
        MvcResult undoResult = mvc.perform(post("/tasks/{id}/undo-delete", taskId)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.restored").value(true))
                .andExpect(jsonPath("$.taskId").exists())
                .andReturn();

        Number restoredTaskId = com.jayway.jsonpath.JsonPath.read(undoResult.getResponse().getContentAsString(), "$.taskId");
        assertThat(tasks.findById(restoredTaskId.longValue())).isPresent();
        assertThat(tasks.findById(restoredTaskId.longValue()).orElseThrow().getName()).isEqualTo("Ajax Deletion Task");
    }

    @Test
    void boardQuickStatusActionWithRedirect() throws Exception {
        Task task = createTask("Board task", TaskStatus.YET_TO_START);

        mvc.perform(post("/tasks/{id}/status", task.getId())
                        .param("status", "IN_PROGRESS")
                        .param("redirectUrl", "/board?date=" + LocalDate.now()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/board?date=" + LocalDate.now()));

        Task updated = tasks.findById(task.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void completeTaskWithDetailsFilesAndLinks() throws Exception {
        Task task = createTask("Complete milestone task", TaskStatus.IN_PROGRESS);
        MockMultipartFile file = new MockMultipartFile("files", "milestone-report.pdf", "application/pdf", "Final milestone report".getBytes());

        mvc.perform(multipart("/tasks/{id}/status", task.getId())
                        .file(file)
                        .param("status", "COMPLETED")
                        .param("completionNote", "Milestone delivered with all test scenarios verified.")
                        .param("linkUrl", "https://github.com/example/repo/pull/123")
                        .param("linkLabel", "Pull Request #123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tasks/" + task.getId()));

        Task completed = tasks.findById(task.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(completed.getCompletionNote()).isEqualTo("Milestone delivered with all test scenarios verified.");
        assertThat(completed.getCompletedAt()).isNotNull();

        // Verify attachment saved
        var taskFiles = attachments.findByTaskIdOrderByCreatedAtDesc(task.getId());
        assertThat(taskFiles).hasSize(1);
        assertThat(taskFiles.get(0).getOriginalName()).isEqualTo("milestone-report.pdf");

        // Verify reference link saved
        var taskLinks = links.findByTaskIdOrderByCreatedAtDesc(task.getId());
        assertThat(taskLinks).hasSize(1);
        assertThat(taskLinks.get(0).getUrl()).isEqualTo("https://github.com/example/repo/pull/123");
        assertThat(taskLinks.get(0).getLabel()).isEqualTo("Pull Request #123");
    }

    @Test
    void completeTaskViaAjaxReturnsJson() throws Exception {
        Task task = createTask("Ajax complete task", TaskStatus.IN_PROGRESS);

        mvc.perform(post("/tasks/{id}/status", task.getId())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("status", "COMPLETED")
                        .param("completionNote", "Completed via modal dialog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.statusLabel").value("Completed"));

        Task completed = tasks.findById(task.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(completed.getCompletionNote()).isEqualTo("Completed via modal dialog");
    }

    @Test
    void reworkSameTaskPreservesDescriptionAndFilesWithNewHistory() throws Exception {
        // 1. Create task with description and initial file attachment
        MockMultipartFile file = new MockMultipartFile("attachment", "design_spec.txt", "text/plain", "Specification v1".getBytes());
        mvc.perform(multipart("/tasks/save")
                        .file(file)
                        .param("name", "Redesign Homepage")
                        .param("description", "Unified homepage with enterprise visual style.")
                        .param("plannedDate", LocalDate.now().toString())
                        .param("deadline", LocalDate.now().plusDays(3).toString())
                        .param("status", "IN_PROGRESS")
                        .param("priority", "HIGH")
                        .param("categoryId", category.getId().toString())
                        .param("linkLabel", "Figma Design")
                        .param("linkUrl", "https://figma.com/design/123"))
                .andExpect(status().is3xxRedirection());

        Task task = tasks.findAll().get(0);
        Long taskId = task.getId();
        assertThat(task.getIteration()).isEqualTo(1);
        assertThat(task.getDescription()).isEqualTo("Unified homepage with enterprise visual style.");
        assertThat(attachments.findByTaskIdAndProgressUpdateIdIsNullOrderByCreatedAtDesc(taskId)).hasSize(1);
        assertThat(links.findByTaskIdOrderByCreatedAtDesc(taskId)).hasSize(1);

        // 2. Add progress update for Iteration 1
        mvc.perform(post("/tasks/{id}/progress", taskId)
                        .param("description", "Completed initial draft mockup"))
                .andExpect(status().is3xxRedirection());
        assertThat(updates.findByTaskIdAndIterationOrderByRecordedAtDesc(taskId, 1)).hasSize(1);

        // 3. Mark completed for Iteration 1
        mvc.perform(post("/tasks/{id}/status", taskId)
                        .param("status", "COMPLETED")
                        .param("completionNote", "Mockup sent for client approval"))
                .andExpect(status().is3xxRedirection());

        Task completed = tasks.findById(taskId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(completed.getIteration()).isEqualTo(1);

        // 4. Trigger Rework on the SAME task
        mvc.perform(post("/tasks/{id}/rework", taskId)
                        .param("plannedDate", LocalDate.now().plusDays(1).toString())
                        .param("deadline", LocalDate.now().plusDays(5).toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tasks/" + taskId));

        // 5. Verify task is reworked: same ID, same description, same files, but iteration = 2 and status = IN_PROGRESS
        Task reworked = tasks.findById(taskId).orElseThrow();
        assertThat(reworked.getId()).isEqualTo(taskId);
        assertThat(reworked.getIteration()).isEqualTo(2);
        assertThat(reworked.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(reworked.getCompletedAt()).isNull();
        assertThat(reworked.getCompletionNote()).isNull();
        assertThat(reworked.getDescription()).isEqualTo("Unified homepage with enterprise visual style.");
        assertThat(attachments.findByTaskIdAndProgressUpdateIdIsNullOrderByCreatedAtDesc(taskId)).hasSize(1);
        assertThat(links.findByTaskIdOrderByCreatedAtDesc(taskId)).hasSize(1);

        // 6. Add progress update for Iteration 2 (new history)
        mvc.perform(post("/tasks/{id}/progress", taskId)
                        .param("description", "Refining layout based on client feedback"))
                .andExpect(status().is3xxRedirection());

        // Verify iteration 1 has 1 update, and iteration 2 has 1 update
        assertThat(updates.findByTaskIdAndIterationOrderByRecordedAtDesc(taskId, 1)).hasSize(1);
        assertThat(updates.findByTaskIdAndIterationOrderByRecordedAtDesc(taskId, 2)).hasSize(1);

        // 7. Render task details page
        mvc.perform(get("/tasks/{id}", taskId))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Iteration 2 (Rework)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Previous iterations history")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Iteration 1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Mockup sent for client approval")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Refining layout based on client feedback")));
    }

    @Test
    void boardRendersDraggableCardsAndGripIcons() throws Exception {
        if (!hasBoard()) return;
        Task task = createTask("Implement drag and drop", TaskStatus.YET_TO_START);

        mvc.perform(get("/board"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("draggable=\"true\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-task-id=\"" + task.getId() + "\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-task-status=\"YET_TO_START\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("icons.svg#grip")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("status-actions")));
    }

    @Test
    void boardHeadingAndStatsRender() throws Exception {
        if (!hasBoard()) return;
        createTask("Board summary test", TaskStatus.IN_PROGRESS);

        mvc.perform(get("/board"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("board-banner"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("board-heading")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("board-stats-card")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Total tasks")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("donut-chart")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("board-controls-toolbar")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("column-yet-to-start")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("column-in-progress")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("column-completed")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("column-blocked"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Blocked/Closed")));
    }

    @Test
    void boardTaskMoveApiEndpointUpdatesStatusAndReturnsJson() throws Exception {
        if (!hasBoard()) return;
        Task task = createTask("Drag task across columns", TaskStatus.YET_TO_START);

        mvc.perform(post("/board/tasks/{id}/move", task.getId())
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.id").value(task.getId()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.statusLabel").value("In progress"));

        Task inProgress = tasks.findById(task.getId()).orElseThrow();
        assertThat(inProgress.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

        mvc.perform(post("/board/tasks/{id}/move", task.getId())
                        .param("status", "COMPLETED")
                        .param("completionNote", "Moved directly to done"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.statusLabel").value("Completed"));

        Task completed = tasks.findById(task.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    @Test
    void boardPersistsDropPositionWithinAndAcrossColumns() throws Exception {
        if (!hasBoard()) return;
        Task first = createTask("First pending card", TaskStatus.YET_TO_START);
        Task second = createTask("Second pending card", TaskStatus.YET_TO_START);
        Task moving = createTask("Moving active card", TaskStatus.IN_PROGRESS);
        String date = first.getPlannedDate().toString();

        mvc.perform(post("/board/tasks/{id}/move", second.getId())
                        .param("date", date)
                        .param("status", "YET_TO_START")
                        .param("before", first.getId().toString()))
                .andExpect(status().isOk());
        mvc.perform(post("/board/tasks/{id}/move", moving.getId())
                        .param("date", date)
                        .param("status", "YET_TO_START")
                        .param("after", second.getId().toString()))
                .andExpect(status().isOk());

        mvc.perform(get("/board").param("date", date))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.stringContainsInOrder(
                        "Second pending card", "Moving active card", "First pending card")));
        assertThat(tasks.findById(moving.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.YET_TO_START);
    }

    @Test
    void boardCarriesOverIncompleteTasksFromPastDatesToToday() throws Exception {
        if (!hasBoard()) return;
        LocalDate yesterday = LocalDate.now().minusDays(1);

        // 1. Task left pending yesterday
        Task pendingFromYesterday = new Task();
        pendingFromYesterday.setName("Unfinished work from yesterday");
        pendingFromYesterday.setDescription("Should automatically roll over to today");
        pendingFromYesterday.setCategory(category);
        pendingFromYesterday.setPlannedDate(yesterday);
        pendingFromYesterday.setPriority(Priority.HIGH);
        pendingFromYesterday.setStatus(TaskStatus.IN_PROGRESS);
        pendingFromYesterday = tasks.save(pendingFromYesterday);

        // 2. Task completed yesterday (should stay on yesterday, NOT roll over)
        Task completedYesterday = new Task();
        completedYesterday.setName("Finished work yesterday");
        completedYesterday.setDescription("Should not roll over");
        completedYesterday.setCategory(category);
        completedYesterday.setPlannedDate(yesterday);
        completedYesterday.setPriority(Priority.LOW);
        completedYesterday.setStatus(TaskStatus.COMPLETED);
        completedYesterday.setCompletedAt(yesterday.atTime(17, 0));
        tasks.save(completedYesterday);

        // 3. Task planned for today
        Task todayTask = createTask("Today regular work", TaskStatus.YET_TO_START);

        // 4. View Today's Board: pending task rolls over with "Rolled over" pill
        mvc.perform(get("/board"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Unfinished work from yesterday")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Rolled over")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Today regular work")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Finished work yesterday"))));

        // 5. View Yesterday's Board: historical view contains both tasks planned for yesterday, no rollover pill
        mvc.perform(get("/board").param("date", yesterday.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Unfinished work from yesterday")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Finished work yesterday")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("rolled-over-pill"))));

        // 6. Complete the rolled-over task today: should stay on Today's board as completed today
        mvc.perform(post("/board/tasks/{id}/move", pendingFromYesterday.getId())
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk());

        mvc.perform(get("/board"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Unfinished work from yesterday")));
    }

    @Test
    void duplicateTaskStartsASeparatePendingIteration() throws Exception {
        Task source = createTask("Monthly review", TaskStatus.COMPLETED);
        source.setCompletionNote("Reviewed");
        source.setIteration(3);
        source = tasks.save(source);

        MvcResult result = mvc.perform(post("/tasks/{id}/duplicate", source.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/tasks/*"))
                .andReturn();

        Task copy = tasks.findAll().stream().filter(task -> task.getName().equals("Monthly review (copy)"))
                .findFirst().orElseThrow();
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/tasks/" + copy.getId());
        assertThat(copy.getId()).isNotEqualTo(source.getId());
        assertThat(copy.getCategory().getId()).isEqualTo(source.getCategory().getId());
        assertThat(copy.getDescription()).isEqualTo(source.getDescription());
        assertThat(copy.getPriority()).isEqualTo(source.getPriority());
        assertThat(copy.getPlannedDate()).isEqualTo(source.getPlannedDate());
        assertThat(copy.getDeadline()).isEqualTo(source.getDeadline());
        assertThat(copy.getStatus()).isEqualTo(TaskStatus.YET_TO_START);
        assertThat(copy.getIteration()).isEqualTo(1);
        assertThat(copy.getCompletedAt()).isNull();
        assertThat(copy.getCompletionNote()).isNull();
    }

    @Test
    void exportTaskQuotesMultilineCellsAndGuardsFormulas() throws Exception {
        Task task = createTask("Expense, monthly", TaskStatus.YET_TO_START);
        task.setDescription(" =SUM(1,2)\nLine with \"quotes\"");
        tasks.save(task);

        mvc.perform(get("/tasks/{id}/export", task.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"task-" + task.getId() + ".csv\""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"Expense, monthly\",\"' =SUM(1,2)\nLine with \"\"quotes\"\"\"")));
    }

    @Test
    void completeTaskAllowsAnyFileTypeAndMultipleFilesAndMultipleLinks() throws Exception {
        Task task = createTask("Task with unrestricted completion files", TaskStatus.IN_PROGRESS);

        MockMultipartFile zipFile = new MockMultipartFile("files", "bundle.zip", "application/zip", "zip-contents".getBytes());
        MockMultipartFile binFile = new MockMultipartFile("files", "firmware.bin", "application/octet-stream", "binary-data".getBytes());
        MockMultipartFile csvFile = new MockMultipartFile("files", "metrics.csv", "text/csv", "col1,col2\n1,2".getBytes());

        mvc.perform(multipart("/tasks/{id}/status", task.getId())
                        .file(zipFile)
                        .file(binFile)
                        .file(csvFile)
                        .param("status", "COMPLETED")
                        .param("completionNote", "Delivered zip archive, firmware, and metric reports.")
                        .param("linkUrls", "https://github.com/project/pull/42")
                        .param("linkLabels", "GitHub PR")
                        .param("linkUrls", "https://docs.google.com/spec")
                        .param("linkLabels", "Architecture Spec"))
                .andExpect(status().is3xxRedirection());

        Task updated = tasks.findById(task.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(updated.getCompletionNote()).isEqualTo("Delivered zip archive, firmware, and metric reports.");

        var savedFiles = attachments.findByTaskIdOrderByCreatedAtDesc(task.getId());
        assertThat(savedFiles).hasSize(3);
        assertThat(savedFiles).extracting("originalName").containsExactlyInAnyOrder("bundle.zip", "firmware.bin", "metrics.csv");

        var savedLinks = links.findByTaskIdOrderByCreatedAtDesc(task.getId());
        assertThat(savedLinks).hasSize(2);
        assertThat(savedLinks).extracting("url").containsExactlyInAnyOrder("https://github.com/project/pull/42", "https://docs.google.com/spec");
        assertThat(savedLinks).extracting("label").containsExactlyInAnyOrder("GitHub PR", "Architecture Spec");
    }

    @Test
    void taskAttachmentsAcceptDiskImagesAndArchives() throws Exception {
        Task task = createTask("Task attachment format check", TaskStatus.IN_PROGRESS);
        byte[] diskImage = {1, 2, 3};
        mvc.perform(multipart("/tasks/{id}/attachments", task.getId())
                        .file(new MockMultipartFile("files", "installer.dmg", "application/x-apple-diskimage", diskImage))
                        .file(new MockMultipartFile("files", "bundle.zip", "application/zip", "zip-contents".getBytes())))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        var savedFiles = attachments.findByTaskIdOrderByCreatedAtDesc(task.getId());
        assertThat(savedFiles).extracting("originalName").containsExactlyInAnyOrder("installer.dmg", "bundle.zip");
        var dmg = savedFiles.stream().filter(file -> file.getOriginalName().endsWith(".dmg")).findFirst().orElseThrow();
        mvc.perform(get("/tasks/{id}/attachments/{fileId}", task.getId(), dmg.getId()).param("preview", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
                .andExpect(content().bytes(diskImage));
    }

    @Test
    void addProgressUpdateAllowsDetailsFilesAndLinksAndUpdatesStatus() throws Exception {
        Task task = createTask("Task for progress update test", TaskStatus.YET_TO_START);

        MockMultipartFile zipFile = new MockMultipartFile("files", "work-in-progress.zip", "application/zip", "zip-bytes".getBytes());
        MockMultipartFile logFile = new MockMultipartFile("files", "output.log", "text/plain", "log data".getBytes());

        mvc.perform(multipart("/tasks/{id}/progress", task.getId())
                        .file(zipFile)
                        .file(logFile)
                        .param("description", "Completed initial architecture draft and benchmarks.")
                        .param("reminder", "Review findings with tech lead on Monday.")
                        .param("linkUrls", "https://github.com/project/commit/abc1234")
                        .param("linkLabels", "Draft Commit")
                        .param("linkUrls", "https://docs.google.com/benchmark-results")
                        .param("linkLabels", "Benchmark Results"))
                .andExpect(status().is3xxRedirection());

        // 1. Task automatically transitioned from YET_TO_START to IN_PROGRESS
        Task updated = tasks.findById(task.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

        // 2. Progress update was saved with description and reminder
        var progressList = updates.findByTaskIdOrderByRecordedAtDesc(task.getId());
        assertThat(progressList).hasSize(1);
        ProgressUpdate savedProgress = progressList.get(0);
        assertThat(savedProgress.getDescription()).isEqualTo("Completed initial architecture draft and benchmarks.");
        assertThat(savedProgress.getReminder()).isEqualTo("Review findings with tech lead on Monday.");

        // 3. Files were attached to the progress update (progressUpdateId set)
        var attachedFiles = attachments.findByProgressUpdateIdOrderByCreatedAtDesc(savedProgress.getId());
        assertThat(attachedFiles).hasSize(2);
        assertThat(attachedFiles).extracting("originalName").containsExactlyInAnyOrder("work-in-progress.zip", "output.log");
        assertThat(attachedFiles).extracting("progressUpdateId").containsOnly(savedProgress.getId());

        // 4. Reference links were saved
        var taskLinks = links.findByTaskIdOrderByCreatedAtDesc(task.getId());
        assertThat(taskLinks).hasSize(2);
        assertThat(taskLinks).extracting("url").containsExactlyInAnyOrder(
                "https://github.com/project/commit/abc1234",
                "https://docs.google.com/benchmark-results"
        );
        assertThat(taskLinks).extracting("label").containsExactlyInAnyOrder("Draft Commit", "Benchmark Results");
        assertThat(taskLinks).extracting("progressUpdateId").containsOnly(savedProgress.getId());
        mvc.perform(get("/tasks/{id}", task.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.stringContainsInOrder(
                        "data-timeline-card", "Attachments · 2", "Links · 2", "progressComposerTitle",
                        "Progress status", "id=\"progressStatus\"", "timelineEntryModal")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("progress-upload"))));

        var replacedFile = attachedFiles.get(0);
        var removedFile = attachedFiles.get(1);
        var editedLink = taskLinks.get(0);
        var removedLink = taskLinks.get(1);
        mvc.perform(multipart("/tasks/{taskId}/progress/{progressId}/edit", task.getId(), savedProgress.getId())
                        .file(new MockMultipartFile("replaceFile." + replacedFile.getId(), "replacement.txt", "text/plain", "Replacement".getBytes()))
                        .file(new MockMultipartFile("files", "new.txt", "text/plain", "New attachment".getBytes()))
                        .param("removeFile." + removedFile.getId(), "on")
                        .param("existingLinkUrl." + editedLink.getId(), "https://example.com/updated")
                        .param("existingLinkLabel." + editedLink.getId(), "Updated reference")
                        .param("removeLink." + removedLink.getId(), "on")
                        .param("linkUrls", "https://example.org/new")
                        .param("linkLabels", "New reference")
                        .param("description", "Revised architecture notes.")
                        .param("reminder", ""))
                .andExpect(redirectedUrl("/tasks/" + task.getId()));

        ProgressUpdate edited = updates.findById(savedProgress.getId()).orElseThrow();
        assertThat(edited.getDescription()).isEqualTo("Revised architecture notes.");
        assertThat(edited.getReminder()).isNull();
        assertThat(edited.getIteration()).isEqualTo(savedProgress.getIteration());
        assertThat(edited.getRecordedAt()).isEqualTo(savedProgress.getRecordedAt());
        assertThat(attachments.findByProgressUpdateIdOrderByCreatedAtDesc(edited.getId()))
                .extracting("originalName").containsExactlyInAnyOrder("replacement.txt", "new.txt");
        assertThat(links.findById(editedLink.getId()).orElseThrow().getUrl()).isEqualTo("https://example.com/updated");
        assertThat(links.findById(removedLink.getId())).isEmpty();
        assertThat(links.findByTaskIdOrderByCreatedAtDesc(task.getId()))
                .extracting("label").containsExactlyInAnyOrder("Updated reference", "New reference");

    }

    @Test
    void boardRendersProgressActionButtonAndCountBadge() throws Exception {
        if (!hasBoard()) return;
        Task task = createTask("Task on board with progress", TaskStatus.IN_PROGRESS);

        mvc.perform(get("/board"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("btn-action-progress")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("open-progress-modal-btn")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("progressUpdateModal")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("btn-action-blue"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("btn-action-green"))));
    }

    @Test
    void boardProgressReopeningCompletedTaskStartsNewIteration() throws Exception {
        Task task = createTask("Completed task that needs rework", TaskStatus.COMPLETED);
        task.setCompletionNote("First iteration delivered");
        tasks.save(task);

        mvc.perform(multipart("/tasks/{id}/progress", task.getId())
                        .param("description", "Revisit the requirements")
                        .param("status", "YET_TO_START")
                        .param("date", task.getPlannedDate().toString())
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("YET_TO_START"));

        Task reworked = tasks.findById(task.getId()).orElseThrow();
        assertThat(reworked.getIteration()).isEqualTo(2);
        assertThat(reworked.getCompletedAt()).isNull();
        assertThat(reworked.getCompletionNote()).isNull();
        assertThat(reworked.getPlannedDate()).isEqualTo(task.getPlannedDate());
        assertThat(updates.findByTaskIdOrderByRecordedAtDesc(task.getId()))
                .singleElement().extracting(ProgressUpdate::getIteration).isEqualTo(2);

        mvc.perform(get("/tasks/{id}", task.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("First iteration delivered")));
    }

    @Test
    void progressUpdateWithoutDescriptionKeepsTaskStatus() throws Exception {
        Task task = createTask("Task waiting to start", TaskStatus.YET_TO_START);

        mvc.perform(multipart("/tasks/{id}/progress", task.getId())
                        .param("description", "")
                        .param("status", "")
                        .param("keepCurrentStatus", "true")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("YET_TO_START"))
                .andExpect(jsonPath("$.description").value("Progress update logged"))
                .andExpect(jsonPath("$.updateCount").value(1));

        assertThat(tasks.findById(task.getId()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.YET_TO_START);
    }

    @Test
    void dragBetweenSectionsRecordsProgressUpdateAndMovesStatus() throws Exception {
        Task task = createTask("Task dragged across sections", TaskStatus.YET_TO_START);

        // 1. Move to IN_PROGRESS: logs as "Moved to In Progress"
        mvc.perform(multipart("/tasks/{id}/progress", task.getId())
                        .param("status", "IN_PROGRESS")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.description").value("Moved to In Progress"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        Task inProgress = tasks.findById(task.getId()).orElseThrow();
        assertThat(inProgress.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

        // 2. Move to YET_TO_START: logs as "Yet to apply"
        mvc.perform(multipart("/tasks/{id}/progress", task.getId())
                        .param("status", "YET_TO_START")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Yet to apply"))
                .andExpect(jsonPath("$.status").value("YET_TO_START"));

        // 3. Move to COMPLETED: logs as "Completed"
        mvc.perform(multipart("/tasks/{id}/progress", task.getId())
                        .param("status", "COMPLETED")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Completed"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 4. Move to BLOCKED: logs as "Blocked/Closed"
        mvc.perform(multipart("/tasks/{id}/progress", task.getId())
                        .param("status", "BLOCKED")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Blocked/Closed"))
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        var progressList = updates.findByTaskIdOrderByRecordedAtDesc(task.getId());
        assertThat(progressList).hasSize(4);
        assertThat(progressList).extracting("description").containsExactly(
                "Blocked/Closed",
                "Completed",
                "Yet to apply",
                "Moved to In Progress"
        );
    }

    @Test
    void multipleFileUploadsAllowedInAllPlaces() throws Exception {
        // 1. Task Creation with multiple files
        MockMultipartFile f1 = new MockMultipartFile("attachments", "file1.pdf", "application/pdf", "pdf1".getBytes());
        MockMultipartFile f2 = new MockMultipartFile("attachments", "file2.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "word2".getBytes());
        MockMultipartFile f3 = new MockMultipartFile("attachments", "file3.png", "image/png", "png3".getBytes());

        mvc.perform(multipart("/tasks/save")
                        .file(f1).file(f2).file(f3)
                        .param("name", "Task with multiple files")
                        .param("linkUrls", "https://example.com/spec?q=one,two", "", "https://example.org/notes")
                        .param("linkLabels", "Project spec", "", "Meeting notes")
                        .param("categoryId", category.getId().toString())
                        .param("plannedDate", LocalDate.now().toString())
                        .param("priority", "HIGH")
                        .param("status", "YET_TO_START"))
                .andExpect(status().is3xxRedirection());

        Task created = tasks.findAll().stream()
                .filter(t -> "Task with multiple files".equals(t.getName()))
                .findFirst().orElseThrow();

        assertThat(attachments.findByTaskIdAndProgressUpdateIdIsNullOrderByCreatedAtDesc(created.getId()))
                .hasSize(3)
                .extracting("originalName")
                .containsExactlyInAnyOrder("file1.pdf", "file2.docx", "file3.png");

        assertThat(links.findByTaskIdOrderByCreatedAtDesc(created.getId()))
                .extracting("url").containsExactlyInAnyOrder("https://example.com/spec?q=one,two", "https://example.org/notes");
        mvc.perform(get("/tasks/{id}/edit", created.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.stringContainsInOrder(
                        "task-editor-files-heading", "file1.pdf", "task-editor-links-heading")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Project spec")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Meeting notes")));

        // 2. Direct upload of multiple files to task
        MockMultipartFile direct1 = new MockMultipartFile("files", "direct1.txt", "text/plain", "data1".getBytes());
        MockMultipartFile direct2 = new MockMultipartFile("files", "direct2.pdf", "application/pdf", "data2".getBytes());

        mvc.perform(multipart("/tasks/{id}/attachments", created.getId())
                        .file(direct1).file(direct2))
                .andExpect(status().is3xxRedirection());

        var foundAttachments = attachments.findByTaskIdAndProgressUpdateIdIsNullOrderByCreatedAtDesc(created.getId());
        assertThat(foundAttachments).hasSize(5);

        // 3. Adding progress update with multiple files
        MockMultipartFile prog1 = new MockMultipartFile("files", "prog1.log", "text/plain", "log1".getBytes());
        MockMultipartFile prog2 = new MockMultipartFile("files", "prog2.zip", "application/zip", "zip2".getBytes());

        mvc.perform(multipart("/tasks/{id}/progress", created.getId())
                        .file(prog1).file(prog2)
                        .param("description", "Progress with 2 files")
                        .param("status", "IN_PROGRESS")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        var updateList = updates.findByTaskIdOrderByRecordedAtDesc(created.getId());
        assertThat(updateList).hasSize(1);
        var progressUpdate = updateList.get(0);
        assertThat(attachments.findByProgressUpdateIdOrderByCreatedAtDesc(progressUpdate.getId()))
                .hasSize(2)
                .extracting("originalName")
                .containsExactlyInAnyOrder("prog1.log", "prog2.zip");

        // 4. Direct upload of multiple files to existing progress update
        MockMultipartFile inlineProg1 = new MockMultipartFile("files", "extra1.jpg", "image/jpeg", "jpg1".getBytes());
        MockMultipartFile inlineProg2 = new MockMultipartFile("files", "extra2.json", "application/json", "json2".getBytes());

        mvc.perform(multipart("/tasks/{taskId}/progress/{progressId}/attachments", created.getId(), progressUpdate.getId())
                        .file(inlineProg1).file(inlineProg2))
                .andExpect(status().is3xxRedirection());

        assertThat(attachments.findByProgressUpdateIdOrderByCreatedAtDesc(progressUpdate.getId()))
                .hasSize(4);

        // 5. Completing task with multiple completion files
        MockMultipartFile comp1 = new MockMultipartFile("files", "final-report.pdf", "application/pdf", "final".getBytes());
        MockMultipartFile comp2 = new MockMultipartFile("files", "delivery.tar", "application/x-tar", "tar".getBytes());

        mvc.perform(multipart("/tasks/{id}/status", created.getId())
                        .file(comp1).file(comp2)
                        .param("status", "COMPLETED")
                        .param("completionNote", "Finished all requirements"))
                .andExpect(status().is3xxRedirection());

        Task finished = tasks.findById(created.getId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(attachments.findByTaskIdAndProgressUpdateIdIsNullOrderByCreatedAtDesc(created.getId()))
                .hasSize(7)
                .extracting("originalName")
                .contains("final-report.pdf", "delivery.tar");
    }

    @Test
    void editTaskPreviewsAndRemovesSavedMaterials() throws Exception {
        Task task = createTask("Task with saved materials", TaskStatus.YET_TO_START);
        mvc.perform(multipart("/tasks/{id}/attachments", task.getId())
                        .file(new MockMultipartFile("files", "report.pdf", "application/pdf", "report".getBytes())))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/tasks/{id}/links", task.getId())
                        .param("label", "Reference").param("url", "https://example.com"))
                .andExpect(status().is3xxRedirection());

        var file = attachments.findByTaskIdAndProgressUpdateIdIsNullOrderByCreatedAtDesc(task.getId()).get(0);
        var link = links.findByTaskIdOrderByCreatedAtDesc(task.getId()).get(0);
        mvc.perform(get("/tasks/{id}/attachments/{fileId}", task.getId(), file.getId()).param("preview", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")));

        mvc.perform(post("/tasks/save")
                        .param("id", task.getId().toString())
                        .param("name", task.getName())
                        .param("plannedDate", task.getPlannedDate().toString())
                        .param("categoryId", category.getId().toString())
                        .param("removeFileIds", file.getId().toString())
                        .param("removeLinkIds", link.getId().toString()))
                .andExpect(status().is3xxRedirection());
        assertThat(attachments.findById(file.getId())).isEmpty();
        assertThat(links.findById(link.getId())).isEmpty();
    }

    @Test
    void resetAppRequiresConfirmationAndClearsWorkspace() throws Exception {
        Task task = createTask("Reset fixture", TaskStatus.YET_TO_START);
        mvc.perform(multipart("/tasks/{id}/progress", task.getId())
                        .file(new MockMultipartFile("files", "reset.txt", "text/plain", "clear this".getBytes()))
                        .param("description", "Progress before reset")
                        .param("linkUrls", "https://example.com/reset"))
                .andExpect(status().is3xxRedirection());
        var attachment = attachments.findAll().get(0);
        var file = java.nio.file.Path.of("target/test-storage/progress-attachments", attachment.getStoredName());
        assertThat(file).exists();

        mvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("resetAppModal")));
        mvc.perform(post("/settings/data/reset"))
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attributeExists("error"));
        assertThat(tasks.findById(task.getId())).isPresent();
        assertThat(file).exists();

        mvc.perform(post("/settings/data/reset").param("confirmed", "true"))
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attribute("workspaceReset", true));
        assertThat(tasks.count()).isZero();
        assertThat(categories.count()).isZero();
        assertThat(updates.count()).isZero();
        assertThat(activity.count()).isZero();
        assertThat(links.count()).isZero();
        assertThat(attachments.count()).isZero();
        assertThat(file).doesNotExist();
    }

    @Test
    void boardGroupsBlockedCardsWithCompletedAndKeepsTheirStatusWhenReordered() throws Exception {
        if (!hasBoard()) return;
        Task completed = createTask("Completed board card", TaskStatus.COMPLETED);
        Task blocked = createTask("Blocked board card", TaskStatus.BLOCKED);

        mvc.perform(get("/board"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.stringContainsInOrder(
                        "column-completed", "Completed board card", "Blocked board card")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("column-blocked"))));

        mvc.perform(post("/board/tasks/{id}/move", blocked.getId())
                        .param("date", blocked.getPlannedDate().toString())
                        .param("status", "BLOCKED")
                        .param("before", completed.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
        mvc.perform(get("/board"))
                .andExpect(content().string(org.hamcrest.Matchers.stringContainsInOrder(
                        "column-completed", "Blocked board card", "Completed board card")));
    }

    @Test
    void taskCreationAndMovementUseTheProgressEditor() throws Exception {
        Task task = createTask("Progress activity task", TaskStatus.IN_PROGRESS);
        TaskActivity move = activity.save(new TaskActivity(task.getId(), task.getName(), category.getId(),
                category.getName(), category.getColor(), TaskActivity.Action.STATUS_CHANGED, TaskStatus.IN_PROGRESS,
                TaskStatus.YET_TO_START, java.time.LocalDateTime.now(), null));
        String prefix = "/tasks/" + task.getId() + "/progress/";
        String creationEdit = prefix + "event/CREATED/edit";
        String moveEdit = prefix + "activity/" + move.getId() + "/edit";

        mvc.perform(get("/tasks/{id}", task.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(creationEdit)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(moveEdit)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-timeline-status=\"IN_PROGRESS\"")));
        var createdAt = LocalDateTime.of(2026, 1, 1, 7, 30);
        mvc.perform(post(creationEdit).param("description", "").param("recordedAt", createdAt.toString()))
                .andExpect(redirectedUrl("/tasks/" + task.getId()));
        assertThat(tasks.findById(task.getId()).orElseThrow().getCreatedAt()).isEqualTo(createdAt);
        assertThat(updates.findByTaskIdOrderByRecordedAtDesc(task.getId())).isEmpty();
        mvc.perform(post(creationEdit).param("description", "Created from the weekly plan"))
                .andExpect(redirectedUrl("/tasks/" + task.getId()));
        mvc.perform(multipart(moveEdit)
                        .file(new MockMultipartFile("files", "notes.txt", "text/plain", "notes".getBytes()))
                        .param("description", "Started the work")
                        .param("recordedAt", "2026-01-01T08:00")
                        .param("linkUrls", "https://example.com/notes"))
                .andExpect(redirectedUrl("/tasks/" + task.getId()));

        var savedMove = activity.findById(move.getId()).orElseThrow();
        Long noteId = savedMove.getProgressUpdateId();
        assertThat(savedMove.getOccurredAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 8, 0));
        assertThat(updates.findById(noteId).orElseThrow().getRecordedAt()).isEqualTo(savedMove.getOccurredAt());
        mvc.perform(post(prefix + noteId + "/edit")
                        .param("description", "Started the work")
                        .param("recordedAt", "2026-01-02T08:00"))
                .andExpect(status().is3xxRedirection());
        var milestoneTime = LocalDateTime.of(2026, 1, 2, 8, 0);
        assertThat(updates.findById(noteId).orElseThrow().getRecordedAt()).isEqualTo(milestoneTime);
        assertThat(activity.findById(move.getId()).orElseThrow().getOccurredAt()).isEqualTo(milestoneTime);
        assertThat(attachments.findByProgressUpdateIdOrderByCreatedAtDesc(savedMove.getProgressUpdateId())).hasSize(1);
        assertThat(links.findByTaskIdOrderByCreatedAtDesc(task.getId())).hasSize(1);
        assertThat(tasks.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        mvc.perform(get("/tasks/{id}", task.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Created from the weekly plan")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Started the work")));
    
        mvc.perform(post(prefix + noteId + "/delete"))
                .andExpect(redirectedUrl("/tasks/" + task.getId()));
        assertThat(updates.findById(noteId)).isEmpty();
        var preserved = activity.findById(move.getId()).orElseThrow();
        assertThat(preserved.getOccurredAt()).isEqualTo(milestoneTime);
        assertThat(preserved.getProgressUpdateId()).isNull();
        assertThat(tasks.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        mvc.perform(get("/tasks/{id}", task.getId())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(moveEdit)));
    }

    @Test
    void correctingCompletionDateUpdatesTaskAndIterationWithoutChangingStatus() throws Exception {
        Task task = createTask("Correct completion", TaskStatus.IN_PROGRESS);
        mvc.perform(post("/tasks/{id}/status", task.getId()).param("status", "COMPLETED"))
                .andExpect(status().is3xxRedirection());
        LocalDateTime corrected = LocalDateTime.of(2026, 1, 3, 9, 30);

        mvc.perform(post("/tasks/{id}/progress/event/COMPLETED/edit", task.getId())
                        .param("description", "").param("recordedAt", corrected.toString()))
                .andExpect(redirectedUrl("/tasks/" + task.getId()))
                .andExpect(flash().attribute("success", "Timeline date saved"));

        assertThat(tasks.findById(task.getId())).get().satisfies(saved -> {
            assertThat(saved.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(saved.getCompletedAt()).isEqualTo(corrected);
        });
        assertThat(iterations.findByTaskIdAndIterationNumber(task.getId(), 1)).get()
                .satisfies(iteration -> assertThat(iteration.getCompletedAt()).isEqualTo(corrected));
        assertThat(activity.findByTaskIdOrderByOccurredAtDescIdDesc(task.getId())).singleElement()
                .satisfies(event -> assertThat(event.getOccurredAt()).isEqualTo(corrected));
        assertThat(updates.findByTaskIdOrderByRecordedAtDesc(task.getId())).isEmpty();
    }

    @Test
    void movingInProgressAfterCompletionRequiresConfirmationBeforeStartingRework() throws Exception {
        Task task = createTask("Reopen from timeline", TaskStatus.IN_PROGRESS);
        var originalStart = task.getCreatedAt().minusHours(1);
        var originalMove = activity.save(new TaskActivity(task.getId(), task.getName(), category.getId(),
                category.getName(), category.getColor(), TaskActivity.Action.STATUS_CHANGED, TaskStatus.IN_PROGRESS,
                TaskStatus.YET_TO_START, originalStart, null));
        String edit = "/tasks/" + task.getId() + "/progress/activity/" + originalMove.getId() + "/edit";
        mvc.perform(post("/tasks/{id}/status", task.getId()).param("status", "COMPLETED"))
                .andExpect(status().is3xxRedirection());
        var completedAt = tasks.findById(task.getId()).orElseThrow().getCompletedAt();
        var restartDate = completedAt.plusDays(1);

        mvc.perform(post(edit).param("description", "Started the rework")
                        .param("recordedAt", restartDate.toString()).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isConflict());
        assertThat(tasks.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(updates.findByTaskIdOrderByRecordedAtDesc(task.getId())).isEmpty();

        mvc.perform(post(edit).param("description", "Started the rework")
                        .param("recordedAt", restartDate.toString()).param("confirmedCompletion", completedAt.toString()))
                .andExpect(redirectedUrl("/tasks/" + task.getId()))
                .andExpect(flash().attribute("success", "Task reopened as iteration 2"));

        assertThat(tasks.findById(task.getId()).orElseThrow()).satisfies(reopened -> {
            assertThat(reopened.getIteration()).isEqualTo(2);
            assertThat(reopened.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
            assertThat(reopened.getCompletedAt()).isNull();
            assertThat(reopened.getPlannedDate()).isEqualTo(task.getPlannedDate());
        });
        assertThat(iterations.findByTaskIdAndIterationNumber(task.getId(), 1).orElseThrow().getCompletedAt())
                .isEqualTo(completedAt);
        assertThat(iterations.findByTaskIdAndIterationNumber(task.getId(), 2).orElseThrow().getStartedAt())
                .isEqualTo(restartDate);
        assertThat(activity.findById(originalMove.getId()).orElseThrow().getOccurredAt())
                .isEqualTo(originalMove.getOccurredAt());
        assertThat(activity.findByTaskIdOrderByOccurredAtDescIdDesc(task.getId()).get(0)).satisfies(event -> {
            assertThat(event.getPreviousStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(event.getOccurredAt()).isEqualTo(restartDate);
            var note = updates.findById(event.getProgressUpdateId()).orElseThrow();
            assertThat(note.getIteration()).isEqualTo(2);
            assertThat(note.getDescription()).isEqualTo("Started the rework");
        });

        mvc.perform(post(edit).param("description", "Started the rework")
                        .param("recordedAt", restartDate.toString()).param("confirmedCompletion", completedAt.toString())
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isConflict());
        assertThat(tasks.findById(task.getId()).orElseThrow().getIteration()).isEqualTo(2);
    }

    @Test
    void taskTimerStartPauseStopLifecycle() throws Exception {
        Task task = createTask("Feature development task", TaskStatus.IN_PROGRESS);

        // Verify task detail page displays the Task Timer section
        mvc.perform(get("/tasks/{id}", task.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Task Timer")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"timerTotalValue\">00:00:00")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"timerSessionsCount\">0")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("btn-timer-start")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("btn-timer-pause")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("btn-timer-stop")));

        // Start timer via AJAX
        mvc.perform(post("/tasks/{id}/timer/start", task.getId())
                        .header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.timerStatus").value("RUNNING"));

        Task runningTask = tasks.findById(task.getId()).orElseThrow();
        assertThat(runningTask.getTimerStatus()).isEqualTo(TaskTimerStatus.RUNNING);
        assertThat(runningTask.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

        if (hasBoard()) {
            mvc.perform(get("/board"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.stringContainsInOrder(
                            "Active timers (1)", "data-board-session", runningTask.getName(), "Total today",
                            "/timer/pause", "/timer/stop")));
        }

        // Check active timer endpoint
        mvc.perform(get("/tasks/timer/active")
                        .header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActive").value(true))
                .andExpect(jsonPath("$.taskId").value(task.getId()));

        // Check status endpoint
        mvc.perform(get("/tasks/{id}/timer/status", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timerStatus").value("RUNNING"));

        // Pause timer
        mvc.perform(post("/tasks/{id}/timer/pause", task.getId())
                        .header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timerStatus").value("PAUSED"));

        Task pausedTask = tasks.findById(task.getId()).orElseThrow();
        assertThat(pausedTask.getTimerStatus()).isEqualTo(TaskTimerStatus.PAUSED);

        // Resume timer
        mvc.perform(post("/tasks/{id}/timer/start", task.getId())
                        .header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timerStatus").value("RUNNING"));

        // Stop timer
        mvc.perform(post("/tasks/{id}/timer/stop", task.getId())
                        .param("note", "Completed sprint task iteration")
                        .header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timerStatus").value("STOPPED"));

        Task stoppedTask = tasks.findById(task.getId()).orElseThrow();
        assertThat(stoppedTask.getTimerStatus()).isEqualTo(TaskTimerStatus.STOPPED);
        assertThat(stoppedTask.getTimerStartedAt()).isNull();

        // Verify task time log was created
        var logs = timeLogs.findByTaskIdOrderByStartedAtDesc(task.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getNote()).isEqualTo("Completed sprint task iteration");

        mvc.perform(get("/tasks/{id}", task.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"timerSessionsCount\">1")));

        // Verify tasks list page displays Time spent column
        mvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Time spent")));
    }

    @Test
    void testMultipleTasksRunningSimultaneously() throws Exception {
        Task taskA = createTask("Concurrent Task Alpha", TaskStatus.IN_PROGRESS);
        Task taskB = createTask("Concurrent Task Beta", TaskStatus.IN_PROGRESS);

        // Start timer on Task A
        mvc.perform(post("/tasks/{id}/timer/start", taskA.getId())
                        .header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.timerStatus").value("RUNNING"));

        // Start timer on Task B (both should now run concurrently)
        mvc.perform(post("/tasks/{id}/timer/start", taskB.getId())
                        .header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.timerStatus").value("RUNNING"));

        // Verify both tasks have RUNNING status in the database
        Task refreshedA = tasks.findById(taskA.getId()).orElseThrow();
        Task refreshedB = tasks.findById(taskB.getId()).orElseThrow();
        assertThat(refreshedA.getTimerStatus()).isEqualTo(TaskTimerStatus.RUNNING);
        assertThat(refreshedB.getTimerStatus()).isEqualTo(TaskTimerStatus.RUNNING);

        // Query multi-timer active endpoint
        mvc.perform(get("/tasks/timer/active")
                        .header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActive").value(true))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.activeTasks").isArray())
                .andExpect(jsonPath("$.activeTasks.length()").value(2));

        // Verify Board view reflects both active timers
        if (hasBoard()) {
            mvc.perform(get("/board"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.stringContainsInOrder(
                            "Active timers (2)", "Concurrent Task Alpha", "Concurrent Task Beta",
                            "/timer/pause", "/timer/stop")));
        }

        // Pause Task A - Task B should continue running
        mvc.perform(post("/tasks/{id}/timer/pause", taskA.getId())
                        .header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timerStatus").value("PAUSED"));

        assertThat(tasks.findById(taskA.getId()).orElseThrow().getTimerStatus()).isEqualTo(TaskTimerStatus.PAUSED);
        assertThat(tasks.findById(taskB.getId()).orElseThrow().getTimerStatus()).isEqualTo(TaskTimerStatus.RUNNING);

        // Active timer endpoint should now report 1 active timer
        mvc.perform(get("/tasks/timer/active")
                        .header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActive").value(true))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.taskId").value(taskB.getId()));

        // Stop Task B
        mvc.perform(post("/tasks/{id}/timer/stop", taskB.getId())
                        .param("note", "Finished Task Beta work session")
                        .header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timerStatus").value("STOPPED"));

        // Stop Task A
        mvc.perform(post("/tasks/{id}/timer/stop", taskA.getId())
                        .param("note", "Finished Task Alpha work session")
                        .header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timerStatus").value("STOPPED"));

        // Verify time logs recorded for both tasks
        assertThat(timeLogs.findByTaskIdOrderByStartedAtDesc(taskA.getId())).hasSize(1);
        assertThat(timeLogs.findByTaskIdOrderByStartedAtDesc(taskB.getId())).hasSize(1);
    }

    @Test
    void overnightTimerKeepsItsSessionAndAllocatesTimeToEachDay() throws Exception {
        LocalDate today = LocalDate.now();
        var midnight = today.atStartOfDay();
        var start = midnight.minusSeconds(30);
        Task task = createTask("Overnight timer", TaskStatus.IN_PROGRESS);
        task.setTimerStatus(TaskTimerStatus.RUNNING);
        task.setTimerStartedAt(start);
        task.setTimerSessionStartedAt(start);
        tasks.save(task);

        mvc.perform(get("/tasks/{id}/timer/status", task.getId()))
                .andExpect(jsonPath("$.activeSessionSeconds").value(org.hamcrest.Matchers.greaterThanOrEqualTo(30)));
        mvc.perform(post("/tasks/{id}/timer/pause", task.getId()).header("Accept", "application/json"))
                .andExpect(jsonPath("$.timerStatus").value("PAUSED"));
        Task paused = tasks.findById(task.getId()).orElseThrow();
        assertThat(paused.getTimerDailySeconds()).containsEntry(today.minusDays(1), 30L);
        assertThat(paused.getTimerDailySeconds().getOrDefault(today, 0L))
                .isEqualTo(paused.getTimerAccumulatedSeconds() - 30);
        mvc.perform(post("/tasks/{id}/timer/start", task.getId()).header("Accept", "application/json"))
                .andExpect(jsonPath("$.timerStatus").value("RUNNING"));
        mvc.perform(get("/tasks/timer/active").param("includeDayTotals", "true"))
                .andExpect(jsonPath("$.activeTasks[0].todayBaseSeconds")
                        .value(org.hamcrest.Matchers.is((int) (paused.getTimerAccumulatedSeconds() - 30))));
        mvc.perform(post("/tasks/{id}/timer/stop", task.getId()).header("Accept", "application/json"))
                .andExpect(jsonPath("$.timerStatus").value("STOPPED"));

        var log = timeLogs.findByTaskIdOrderByStartedAtDesc(task.getId()).get(0);
        assertThat(log.getStartedAt()).isEqualTo(start);
        assertThat(log.secondsOn(today.minusDays(1))).isEqualTo(30);
        assertThat(log.secondsOn(today)).isEqualTo(log.getDurationSeconds() - 30);
    }

    @Test
    void timerCannotStartOutsideInProgress() throws Exception {
        for (TaskStatus status : List.of(TaskStatus.YET_TO_START, TaskStatus.COMPLETED, TaskStatus.BLOCKED)) {
            Task task = createTask("Timer unavailable for " + status.getLabel(), status);
            mvc.perform(post("/tasks/{id}/timer/start", task.getId()).header("Accept", "application/json"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false));
            Task unchanged = tasks.findById(task.getId()).orElseThrow();
            assertThat(unchanged.getStatus()).isEqualTo(status);
            assertThat(unchanged.getTimerStatus()).isEqualTo(TaskTimerStatus.STOPPED);
        }
    }

    @Test
    void dashboardCalendarHidesDatesOutsideSelectedMonth() throws Exception {
        mvc.perform(get("/dashboard").param("date", "2026-11-01"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("calendarOpeningWeek", LocalDate.of(2026, 10, 26)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-date=\"2026-11-01\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-date=\"2026-11-30\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("data-date=\"2026-10-31\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("data-date=\"2026-12-01\""))));
    }

    @Test
    void tasksPageRendersFilterMemoryAttributesAndClearLinks() throws Exception {
        createTask("Filter test task", TaskStatus.IN_PROGRESS);

        mvc.perform(get("/tasks").param("category", category.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-clear-filter=\"tasks\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("clear=true")));
    }

    @Test
    void historyPageRendersFilterMemoryAttributesAndClearLinks() throws Exception {
        mvc.perform(get("/history").param("category", category.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-clear-filter=\"history\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("clear=true")));
    }

    @Test
    void dashboardKeepsTheDefaultWeekWhenTodayIsExplicitlySelected() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate firstMonthWeek = today.withDayOfMonth(1)
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate preferred = today.getDayOfWeek().getValue() <= 3 ? monday.minusWeeks(1) : monday;
        LocalDate expected = preferred.isBefore(firstMonthWeek) ? firstMonthWeek : preferred;
        mvc.perform(get("/dashboard").param("date", today.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("calendarOpeningWeek", expected));
    }

    @Test
    void progressUpdateCustomDateAndRemovalWorkflow() throws Exception {
        Task task = createTask("Progress Date & Remove Task", TaskStatus.IN_PROGRESS);

        // 1. Add progress update with custom recordedAt date
        LocalDateTime pastDate = LocalDateTime.of(2026, 11, 15, 14, 30);
        mvc.perform(multipart("/tasks/{id}/progress", task.getId())
                        .param("description", "Initial progress logged in the past")
                        .param("reminder", "Don't forget tests")
                        .param("recordedAt", pastDate.toString())
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        var progressList = updates.findByTaskIdOrderByRecordedAtDesc(task.getId());
        assertThat(progressList).hasSize(1);
        ProgressUpdate initialUpdate = progressList.get(0);
        assertThat(initialUpdate.getDescription()).isEqualTo("Initial progress logged in the past");
        assertThat(initialUpdate.getRecordedAt()).isEqualTo(pastDate);

        // 2. View task detail - verify date is rendered and delete url is present
        mvc.perform(get("/tasks/{id}", task.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-progress-recorded-at=\"2026-11-15T14:30\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/tasks/" + task.getId() + "/progress/" + initialUpdate.getId() + "/delete")));

        // 3. Edit the progress update date and description
        LocalDateTime updatedDate = LocalDateTime.of(2026, 11, 18, 9, 15);
        mvc.perform(post("/tasks/{taskId}/progress/{progressId}/edit", task.getId(), initialUpdate.getId())
                        .param("description", "Updated progress description")
                        .param("reminder", "Updated reminder")
                        .param("recordedAt", updatedDate.toString())
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ProgressUpdate refreshed = updates.findById(initialUpdate.getId()).orElseThrow();
        assertThat(refreshed.getDescription()).isEqualTo("Updated progress description");
        assertThat(refreshed.getRecordedAt()).isEqualTo(updatedDate);

        // 4. Delete the progress update
        mvc.perform(post("/tasks/{taskId}/progress/{progressId}/delete", task.getId(), initialUpdate.getId())
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(updates.findById(initialUpdate.getId())).isEmpty();
    }

    private Task createTask(String name, TaskStatus status) {
        Task task = new Task();
        task.setName(name); task.setDescription("Test task"); task.setCategory(category);
        task.setPlannedDate(LocalDate.now()); task.setDeadline(LocalDate.now().plusDays(1));
        task.setPriority(Priority.MEDIUM); task.setStatus(status);
        return tasks.save(task);
    }
}
