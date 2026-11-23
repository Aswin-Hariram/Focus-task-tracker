# Focus — Personal Productivity Tracker

Focus is a server-rendered productivity application for planning tasks, recording progress, managing supporting files and links, reviewing overdue work, and measuring completion trends.

## Included features

- Dashboard with real task counts, today’s work, completion rate, and overdue preview.
- Daily three-column Kanban board with date navigation and category filtering.
- Task creation, editing, archiving, searching, priority, deadlines, and status changes.
- Progress timeline with reminder notes and retained completion details.
- Local file attachments with type validation.
- Validated HTTP/HTTPS reference links.
- Completed-task history, overdue views, category management, analytics, and CSV export.
- Responsive Custom Enterprise UI built with Thymeleaf, Bootstrap 5, custom CSS, and vanilla JavaScript.
- Flyway-managed schema, project-bundled native PostgreSQL, and H2 only for isolated automated tests.

## Requirements

- JDK 17
- Maven 3.8+

## Run locally

The application includes native PostgreSQL binaries and starts its own PostgreSQL process automatically. No Docker or separate PostgreSQL installation is required.

```bash
mvn clean test
mvn -pl productivity-app -am spring-boot:run
```

To run **without the board module** (or without other optional feature modules):

```bash
# Run without productivity-board:
mvn -pl productivity-app -am spring-boot:run -Dwithout-board

# Run without any optional feature modules (board, overdue, history, analytics):
mvn -pl productivity-app -am spring-boot:run -Dwithout-board -Dwithout-overdue -Dwithout-history -Dwithout-analytics
```

Open [http://localhost:8484](http://localhost:8484). On the first local launch, the application creates a small set of editable starter records. Set `APP_SEED_DEMO_DATA=false` to begin with an empty database.

PostgreSQL data persists under `postgres-data/` (separate from the `storage/` directory). The bundled server listens on local port `5433` by default and stops cleanly with the application. Flyway creates and validates the schema automatically.

To run the packaged application:

```bash
java -jar productivity-app/target/productivity-app-1.0.0-SNAPSHOT.jar
```

Use another local database port when `5433` is occupied:

```bash
EMBEDDED_POSTGRES_PORT=5544 java -jar productivity-app/target/productivity-app-1.0.0-SNAPSHOT.jar
```

## Production PostgreSQL

The bundled database is intended for the single-user local application. The `prod` profile disables it and requires an externally managed PostgreSQL database with explicit credentials:

```bash
SPRING_PROFILES_ACTIVE=prod \
DB_URL=jdbc:postgresql://database-host:5432/productivity_tracker \
DB_USERNAME=productivity \
DB_PASSWORD=replace_with_a_secret \
java -jar productivity-app/target/productivity-app-1.0.0-SNAPSHOT.jar
```

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8484` | HTTP port |
| `APP_STORAGE_ROOT` | `./storage` | Attachments and exported files location |
| `APP_POSTGRES_DATA_DIR` | `./postgres-data` | Bundled PostgreSQL database directory |
| `APP_SEED_DEMO_DATA` | `true` locally, `false` in prod | Starter records |
| `APP_EMBEDDED_POSTGRES_ENABLED` | `true` | Start bundled PostgreSQL; set to `false` for an external database |
| `EMBEDDED_POSTGRES_PORT` | `5433` | Bundled PostgreSQL local port |
| `DB_URL` | unset locally | External PostgreSQL JDBC URL; setting it bypasses bundled PostgreSQL |
| `DB_USERNAME` | `postgres` locally | External database username |
| `DB_PASSWORD` | empty locally | Database password |

## Architecture

The Maven reactor is a modular monolith. Category, task, progress, and attachment modules own persistence; board, overdue, history, and analytics derive their views from those records. `productivity-app` integrates the modules and owns the Thymeleaf web layer, static assets, configuration, and migrations.

## Native Installers & Executables

Build the local distributions from source. On macOS, the packaging script generates the DMG, portable ZIP, and standalone JAR under [`bin/`](bin/).

```bash
./packaging/package-macos.sh
```

To build just the standalone JAR on macOS or Linux:

```bash
mvn package
cp productivity-app/target/productivity-app-1.0.0-SNAPSHOT.jar bin/focus.jar
```

```bash
# Universal CLI & Service controller:
./bin/focus {run|start|stop|restart|status|logs|update|uninstall}

# Standalone Updaters:
./bin/update             # macOS & Linux auto-dispatcher
./bin/mac/Focus-Update   # Native universal Mach-O binary (macOS)

# Standalone Uninstallers:
./bin/uninstall          # macOS & Linux auto-dispatcher
./bin/mac/Focus-Uninstall # Native universal Mach-O binary (macOS)
```

For full details, see [`bin/README.md`](file:///Users/aswinhariram/My%20Creativity/Productivity_Management/personal-productivity-tracker/bin/README.md).

## Tests

```bash
mvn test
```

The application integration tests start the full Spring context against an in-memory H2 database, run Flyway, render primary routes, validate task input, verify the task completion lifecycle, and check CSV export.
