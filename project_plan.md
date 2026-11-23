# Personal Productivity Tracker
## Software Development Roadmap

**Project:** Personal Productivity Tracker  
**Version:** 1.0  
**Technology:** Java 17, Spring Boot, Thymeleaf, PostgreSQL  
**Architecture:** Modular Monolith – Multi-module Maven  
**Deployment Target:** Windows (`.exe`) and macOS (`.dmg`)

---

## 1. Project Overview

The Personal Productivity Tracker is a desktop-installable productivity application designed to manage daily tasks, deadlines, progress updates, documents, and personal productivity.

The application will provide:

- Task creation and management.
- Customizable task categories.
- Daily Kanban boards.
- Progress tracking and completion records.
- Document and reference-link management.
- Overdue task monitoring.
- Completed task history.
- Productivity analytics.
- Windows and macOS installers.

The application will use a single Spring Boot backend, Thymeleaf frontend, and PostgreSQL database.

## 2. Technology Stack

| Component | Technology |
|---|---|
| Programming language | Java 17 |
| Backend framework | Spring Boot |
| Frontend | Thymeleaf, HTML, CSS, JavaScript |
| Styling | Bootstrap |
| Database | PostgreSQL |
| ORM | Spring Data JPA / Hibernate |
| Database migrations | Flyway |
| Build system | Maven |
| Architecture | Modular Monolith |
| File storage | Local filesystem |
| Desktop packaging | Java jpackage |
| Version control | Git |

## 3. Project Module Structure

The project will contain ten Maven modules.

| Module | Responsibility |
|---|---|
| productivity-core | Shared utilities, exceptions and common configuration |
| productivity-category | Category creation and management |
| productivity-task | Task creation, editing and status management |
| productivity-progress | Progress updates and completion records |
| productivity-attachment | Document uploads and reference links |
| productivity-board | Daily Kanban board |
| productivity-overdue | Overdue task identification |
| productivity-history | Completed task history and search |
| productivity-analytics | Reports and productivity statistics |
| productivity-app | Spring Boot entry point, Thymeleaf UI and module integration |

### Module ownership

The following modules own persistent data:

- Category
- Task
- Progress
- Attachment

Board, Overdue, History and Analytics initially derive their results from the existing database records rather than maintaining duplicate data.

## 4. Development Phases

### Phase 1 – Project Foundation

**Objective:** Establish the project structure and development environment.

Tasks:

- [x] Create the root Maven parent project.
- [x] Create all ten Maven modules.
- [x] Configure Java 17.
- [x] Configure Spring Boot dependencies.
- [x] Configure module dependencies.
- [x] Create the Spring Boot entry point.
- [x] Configure Thymeleaf.
- [x] Configure PostgreSQL connection.
- [x] Configure Flyway.
- [x] Configure local file storage.
- [x] Verify Maven build and application startup.

**Deliverable:** A working modular Maven application with PostgreSQL connectivity.

### Phase 2 – Core and Category Management

**Objective:** Implement common functionality and category management.

Core module:

- [x] Common constants.
- [x] Shared exceptions.
- [x] Global exception handling.
- [x] Common response structures.
- [x] Date and time utilities.

Category module:

- [x] Category entity.
- [x] Category repository.
- [x] Category service.
- [x] Category controller.
- [x] Create category.
- [x] Edit category.
- [x] Archive category.
- [x] List and filter categories.

**Deliverable:** Functional category management.

### Phase 3 – Task Management

**Objective:** Implement the main task lifecycle.

Task creation fields:

- Task name.
- Category.
- Description.
- Planned date.
- Deadline.
- Initial status.
- Documents and reference links through the Attachment module.

Development tasks:

- [x] Task entity.
- [x] Task repository.
- [x] Task DTOs.
- [x] Task service.
- [x] Task controller.
- [x] Create tasks.
- [x] Edit tasks.
- [x] Archive tasks.
- [x] Retrieve task details.
- [x] Change task status.
- [x] Validate task input.
- [x] Implement task filtering and searching.

Initial task statuses:

- Yet to Start.
- In Progress.
- Completed.

**Deliverable:** Complete task CRUD and status management.

### Phase 4 – Progress and Completion Management

**Objective:** Record work performed on individual tasks.

Development tasks:

- [x] Create TaskUpdate entity.
- [x] Create progress repository.
- [x] Implement progress service.
- [x] Add progress descriptions.
- [x] Add things-to-remember notes.
- [x] Support multiple progress updates per task.
- [x] Record completion descriptions.
- [x] Record completion timestamps.
- [x] Display progress timeline.
- [x] Preserve task information after completion.

Completion records should remain available even after a task is completed.

**Deliverable:** Full task progress history and completion tracking.

### Phase 5 – Attachment and Link Management

**Objective:** Support documents and external references.

Development tasks:

- [x] Implement attachment entity.
- [x] Implement link entity.
- [x] Upload documents.
- [x] Validate file type.
- [x] Validate file size.
- [x] Generate unique stored filenames.
- [x] Save files to local storage.
- [x] Store file metadata in PostgreSQL.
- [x] Associate files with tasks.
- [x] Associate files with progress updates.
- [x] Download attachments.
- [x] Delete attachments safely.
- [x] Save and retrieve external links.

Storage structure:

```text
storage/
├── task-attachments/
├── progress-attachments/
└── exports/
```

**Deliverable:** Working document and reference management.

### Phase 6 – Daily Kanban Board

**Objective:** Build the primary daily productivity interface.

Development tasks:

- [x] Create daily board service.
- [x] Retrieve tasks by planned date.
- [x] Display Yet to Start column.
- [x] Display In Progress column.
- [x] Display Completed column.
- [x] Support date navigation.
- [x] Add category filters.
- [x] Add task cards.
- [x] Support status changes.
- [x] Refresh board after task updates.

**Deliverable:** Interactive daily Kanban dashboard.

### Phase 7 – Overdue Task Management

**Objective:** Identify tasks that have passed their deadlines.

Development tasks:

- [x] Implement overdue task query.
- [x] Exclude completed tasks.
- [x] Exclude tasks without deadlines.
- [x] Calculate overdue duration.
- [x] Display overdue tasks separately.
- [x] Filter overdue tasks by category.
- [x] Integrate overdue information into the dashboard.

Overdue rule:

A task is overdue when its deadline is earlier than the current date and its status is not Completed.

**Deliverable:** Dedicated overdue task management.

### Phase 8 – Completed Task History

**Objective:** Make previous work searchable and reusable.

Development tasks:

- [x] Retrieve completed tasks.
- [x] Search completed tasks.
- [x] Filter by completion date.
- [x] Filter by category.
- [x] Display completion descriptions.
- [x] Display progress timeline.
- [x] Retrieve associated documents.
- [x] Retrieve reference links.
- [x] Display original task details.

**Deliverable:** Searchable completed-task history.

### Phase 9 – Analytics and Reports

**Objective:** Provide insights into productivity.

Development tasks:

- [x] Daily task statistics.
- [x] Weekly task statistics.
- [x] Monthly task statistics.
- [x] Created task counts.
- [x] Completed task counts.
- [x] Pending task counts.
- [x] Overdue task counts.
- [x] Completion rate calculations.
- [x] Category-wise task distribution.
- [x] Category-wise completion statistics.
- [x] Historical productivity trends.
- [x] Export reports.

**Deliverable:** Productivity analytics dashboard.

### Phase 10 – Frontend Integration and UI Polish

**Objective:** Deliver a consistent user experience.

Frontend pages:

- [x] Dashboard.
- [x] Daily Kanban board.
- [x] Create task.
- [x] Edit task.
- [x] Task details.
- [x] Progress timeline.
- [x] Completed task history.
- [x] Overdue tasks.
- [x] Category management.
- [x] Analytics.
- [x] Settings.

Additional improvements:

- [x] Responsive layout.
- [x] Consistent navigation.
- [x] Form validation messages.
- [x] Confirmation dialogs.
- [x] Loading states.
- [x] Empty-state screens.
- [x] Error pages.
- [x] Search and filtering UI.

**Deliverable:** Fully integrated Thymeleaf application.

### Phase 11 – Testing and Reliability

**Objective:** Verify application correctness and reliability.

Development tasks:

- [x] Unit tests for services.
- [x] Repository tests.
- [x] Controller tests.
- [x] Module integration tests.
- [x] Database migration tests.
- [x] File upload tests.
- [x] Task lifecycle tests.
- [x] Deadline and overdue tests.
- [x] Progress history tests.
- [x] Frontend validation tests.
- [x] Application startup tests.
- [x] Backup and restore tests.

**Deliverable:** Tested and stable application.

### Phase 12 – Desktop Packaging

**Objective:** Distribute the application as installable desktop software.

Packaging technology: Java `jpackage`.

Windows:

- [x] Generate executable application package.
- [x] Create `.exe` installer.
- [x] Add Start Menu shortcut.
- [x] Configure installation directory.
- [x] Add uninstaller.
- [x] Test on a clean Windows machine.

macOS:

- [x] Generate macOS application package.
- [x] Create `.dmg` installer.
- [x] Configure Applications folder installation.
- [x] Add application icon.
- [x] Test on a clean Mac.
- [x] Configure code signing and notarization.

Runtime and database:

- [x] Bundle Java runtime.
- [x] Automate PostgreSQL setup.
- [x] Initialize the database automatically.
- [x] Configure application startup.
- [x] Preserve user data during upgrades.
- [x] Provide backup and restore functionality.

**Deliverable:** Windows and macOS installers.

---

## 5. Database Design Roadmap

The initial PostgreSQL schema will contain five primary tables.

| Table | Description |
|---|---|
| categories | Category information |
| tasks | Main task records |
| task_updates | Progress and completion history |
| task_attachments | Uploaded file metadata |
| task_links | External reference URLs |

Additional implementation requirements:

- Use primary keys and foreign keys.
- Add indexes for common filters.
- Use appropriate database constraints.
- Track creation and modification timestamps.
- Use Flyway for schema versioning.
- Avoid duplicate business data.
- Store actual documents outside PostgreSQL.

## 6. Important Business Rules

1. A task must have a name.
2. A task can belong to a category.
3. A task can have multiple progress updates.
4. A task can have multiple attachments and links.
5. A completed task must retain its completion record.
6. Completed tasks must not be classified as overdue.
7. Tasks without deadlines must not automatically become overdue.
8. Planned date and deadline must be separate fields.
9. Archiving a category must not delete its historical tasks.
10. Deleting task records must not accidentally leave orphaned attachment files.

## 7. Suggested Development Timeline

This is an indicative schedule for one developer, assuming consistent part-time development.

| Week | Focus | Milestone |
|---|---|---|
| 1 | Project foundation | Maven project builds |
| 2 | Core and Category | Category management |
| 3 | Task management | Task CRUD |
| 4 | Progress and Completion | Progress history |
| 5 | Attachments | File and link support |
| 6 | Daily Board | Kanban dashboard |
| 7 | Overdue and History | Task tracking views |
| 8 | Analytics | Reports dashboard |
| 9 | Frontend polish | Complete user interface |
| 10 | Testing | Stable application |
| 11 | Desktop packaging | Windows and macOS installers |

The schedule is an estimate and can be adjusted based on testing and packaging complexity.

## 8. Final Project Deliverables

At the end of development, the project should contain:

| Deliverable | Expected output |
|---|---|
| Source code | Complete multi-module Maven project |
| Database | PostgreSQL schema and Flyway migrations |
| Frontend | Thymeleaf web interface |
| Backend | Spring Boot business modules |
| File management | Local document storage |
| Tests | Unit and integration tests |
| Windows installer | `.exe` |
| macOS installer | `.dmg` |
| Documentation | Setup and user guide |
| Backup system | Database and file backup/restore |

## 9. Definition of Done

The project will be considered ready for its first stable release when:

- All planned task workflows work correctly.
- Users can create, update, complete, and search tasks.
- Daily Kanban boards display correct tasks.
- Overdue tasks are calculated correctly.
- Progress updates and attachments are preserved.
- Analytics reflect actual database records.
- Data survives application restarts and upgrades.
- Windows and macOS installers work on clean machines.
- Application data can be backed up and restored.

---

## 10. Recommended Implementation Strategy

Build the application incrementally.

Do not implement all modules simultaneously. Complete and test each module before integrating the next one.

**Recommended first milestone:**

1. Create the Maven project.
2. Configure PostgreSQL.
3. Implement Core.
4. Implement Category.
5. Implement Task CRUD.
6. Verify the complete task creation workflow.

Once these are stable, proceed to Progress and Attachment.

**Primary architectural principle:** Each module should own its business responsibility, expose clear service interfaces, and avoid duplicating data or business logic.

---

**Project roadmap status:** Complete.  
**Current development stage:** Release ready (Version 1.0).  
**Next milestone:** Distribution and packaging.