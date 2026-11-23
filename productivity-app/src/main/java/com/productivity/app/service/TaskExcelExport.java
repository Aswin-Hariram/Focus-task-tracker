package com.productivity.app.service;

import com.productivity.task.entity.Task;
import com.productivity.task.enums.Priority;
import com.productivity.task.enums.TaskStatus;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PatternFormatting;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskExcelExport {
    private static final int HEADER_ROW = 2;
    private static final int FIRST_DATA_ROW = HEADER_ROW + 1;
    private final TaskExportData data;

    TaskExcelExport(TaskExportData data) {
        this.data = data;
    }

    public byte[] task(Long id) throws IOException {
        return export(data.task(id));
    }

    public byte[] allTasks() throws IOException {
        return export(data.allTasks());
    }

    public byte[] completed(String query, Long category, LocalDate from, LocalDate to) throws IOException {
        return export(data.completed(query, category, from, to));
    }

    private byte[] export(List<Task> records) throws IOException {
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var styles = new Styles(book);
            var tasks = new Sheet(book, styles, "Tasks", 2,
                    col("Date", 17), col("Task", 35), col("Description", 52), col("Status", 21),
                    col("Due Date", 17), col("Priority", 14), col("Time Spent", 17),
                    col("Task Completed Date", 23), col("Category", 24), col("Completion Note", 44),
                    col("Iteration", 12), col("Archived", 12), col("Task ID", 14), col("Created At", 23),
                    col("Last Updated", 23), col("Board Position", 18), col("Timer Status", 17),
                    col("Timer Started", 23), col("Timer Session Started", 24), col("Current Session", 20),
                    col("Category ID", 16), col("Category Color", 18), col("Category Archived", 21));
            var progress = new Sheet(book, styles, "Progress Updates", 2,
                    col("Task ID", 14), col("Task", 35), col("Progress ID", 16), col("Iteration", 12),
                    col("Progress Date", 23), col("Progress Note", 60), col("Reminder", 42),
                    col("Created At", 23), col("Last Updated", 23));
            var timeline = new Sheet(book, styles, "Timeline", 2,
                    col("Task ID", 14), col("Task", 35), col("Event ID", 14), col("Event Date", 23),
                    col("Event", 32), col("Status", 21), col("Previous Status", 21), col("Note", 52),
                    col("Progress ID", 16), col("Category", 24), col("Category ID", 16), col("Category Color", 18));
            var iterations = new Sheet(book, styles, "Iterations", 2,
                    col("Task ID", 14), col("Task", 35), col("Iteration", 12), col("Started At", 23),
                    col("Completed At", 23), col("Completion Note", 52), col("Created At", 23), col("Last Updated", 23));
            var timeLogs = new Sheet(book, styles, "Time Logs", 2,
                    col("Task ID", 14), col("Task", 35), col("Time Log ID", 16), col("Started At", 23),
                    col("Ended At", 23), col("Time Spent", 16), col("Note", 52), col("Created At", 23), col("Last Updated", 23));
            var daily = new Sheet(book, styles, "Daily Time", 2,
                    col("Task ID", 14), col("Task", 35), col("Time Log ID", 16), col("Date", 17), col("Time Spent", 16));
            var links = new Sheet(book, styles, "Reference Links", 2,
                    col("Task ID", 14), col("Task", 35), col("Link ID", 12), col("Progress ID", 16),
                    col("Label", 32), col("URL", 65), col("Created At", 23), col("Last Updated", 23));
            var files = new Sheet(book, styles, "Attachments", 2,
                    col("Task ID", 14), col("Task", 35), col("File ID", 12), col("Progress ID", 16),
                    col("Filename", 42), col("File Type", 32), col("Size (bytes)", 17), col("Download Path", 42),
                    col("Created At", 23), col("Last Updated", 23));

            for (Task task : records) {
                String id = id(task.getId());
                String name = task.getName();
                var category = task.getCategory();
                tasks.add(task.getPlannedDate(), name, task.getDescription(), task.getStatus().getLabel(), task.getDeadline(),
                        task.getPriority().getLabel(), Duration.ofSeconds(task.getTotalTimeSpentSeconds()),
                        task.getCompletedAt(), category.getName(), task.getCompletionNote(), task.getIteration(),
                        task.isArchived(), id, task.getCreatedAt(), task.getUpdatedAt(), task.getBoardPosition(),
                        task.getTimerStatus().name(), task.getTimerStartedAt(), task.getTimerSessionStartedAt(),
                        Duration.ofSeconds(task.getTimerAccumulatedSeconds()), id(category.getId()), category.getColor(), category.isArchived());
                var details = data.details(task.getId());
                for (var value : details.progress()) {
                    progress.add(id, name, id(value.getId()), value.getIteration(), value.getRecordedAt(),
                            value.getDescription(), value.getReminder(), value.getCreatedAt(), value.getUpdatedAt());
                }
                for (var value : details.events()) {
                    timeline.add(id, value.getTaskName(), id(value.getId()), value.getOccurredAt(), value.getSummary(),
                            status(value.getStatus()), status(value.getPreviousStatus()), value.getNote(),
                            id(value.getProgressUpdateId()), value.getCategoryName(), id(value.getCategoryId()), value.getCategoryColor());
                }
                for (var value : details.iterations()) {
                    iterations.add(id, name, value.getIterationNumber(), value.getStartedAt(), value.getCompletedAt(),
                            value.getCompletionNote(), value.getCreatedAt(), value.getUpdatedAt());
                }
                for (var value : details.timeLogs()) {
                    timeLogs.add(id, name, id(value.getId()), value.getStartedAt(), value.getEndedAt(),
                            Duration.ofSeconds(value.getDurationSeconds()), value.getNote(), value.getCreatedAt(), value.getUpdatedAt());
                    addDaily(daily, id, name, id(value.getId()), value.getDailySeconds());
                }
                addDaily(daily, id, name, null, task.getTimerDailySeconds());
                for (var value : details.links()) {
                    links.add(id, name, id(value.getId()), id(value.getProgressUpdateId()), value.getLabel(),
                            new WebLink(value.getUrl()), value.getCreatedAt(), value.getUpdatedAt());
                }
                for (var value : details.attachments()) {
                    files.add(id, name, id(value.getId()), id(value.getProgressUpdateId()), value.getOriginalName(),
                            value.getContentType(), value.getSizeBytes(), "/tasks/" + id + "/attachments/" + value.getId(),
                            value.getCreatedAt(), value.getUpdatedAt());
                }
            }

            tasks.taskControls();
            tasks.finish();
            for (Sheet detail : List.of(progress, timeline, iterations, timeLogs, daily, links, files)) {
                if (detail.hasData()) detail.finish();
                else book.removeSheetAt(book.getSheetIndex(detail.sheet));
            }
            book.write(output);
            return output.toByteArray();
        }
    }

    private static void addDaily(Sheet sheet, String taskId, String taskName, String logId, Map<LocalDate, Long> totals) {
        if (totals == null) return;
        new TreeMap<>(totals).forEach((date, seconds) -> sheet.add(taskId, taskName, logId, date, Duration.ofSeconds(seconds)));
    }

    private static XSSFColor rgb(int value) {
        return new XSSFColor(new byte[]{(byte) (value >> 16), (byte) (value >> 8), (byte) value});
    }

    private static String id(Long value) {
        return Objects.toString(value, null);
    }

    private static String status(TaskStatus value) {
        return value == null ? null : value.getLabel();
    }

    private static Column col(String label, int width) {
        return new Column(label, width);
    }

    private record Column(String label, int width) {}

    private record WebLink(String url) {
        boolean isClickable() {
            try {
                URI parsed = URI.create(url);
                return parsed.getHost() != null && ("http".equalsIgnoreCase(parsed.getScheme()) || "https".equalsIgnoreCase(parsed.getScheme()));
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }
    }

    private static class Sheet {
        private final XSSFSheet sheet;
        private final Styles styles;
        private final Column[] columns;
        private int nextRow = FIRST_DATA_ROW;

        Sheet(XSSFWorkbook book, Styles styles, String title, int frozenColumns, Column... columns) {
            this.sheet = book.createSheet(title);
            this.styles = styles;
            this.columns = columns;
            sheet.setDisplayGridlines(false);
            sheet.createFreezePane(frozenColumns, FIRST_DATA_ROW);
            sheet.setTabColor(rgb(0x1C3852));
            var titleRow = sheet.createRow(0);
            titleRow.setHeightInPoints(27);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(title);
            titleCell.setCellStyle(styles.title);
            var header = sheet.createRow(HEADER_ROW);
            header.setHeightInPoints(24);
            for (int i = 0; i < columns.length; i++) {
                sheet.setColumnWidth(i, columns[i].width() * 256);
                var cell = header.createCell(i);
                cell.setCellValue(columns[i].label());
                cell.setCellStyle(styles.header);
            }
        }

        void add(Object... values) {
            if (values.length != columns.length) throw new IllegalArgumentException("Values must match the worksheet columns");
            var row = sheet.createRow(nextRow++);
            boolean banded = (row.getRowNum() - FIRST_DATA_ROW) % 2 == 1;
            int lines = 1;
            for (int i = 0; i < values.length; i++) {
                Cell cell = row.createCell(i);
                Object value = values[i];
                if (value instanceof LocalDate date) {
                    cell.setCellValue(date);
                    cell.setCellStyle(styles.date.forRow(banded));
                } else if (value instanceof LocalDateTime dateTime) {
                    cell.setCellValue(dateTime);
                    cell.setCellStyle(styles.dateTime.forRow(banded));
                } else if (value instanceof Duration duration) {
                    cell.setCellValue(duration.toSeconds() / 86_400.0);
                    cell.setCellStyle(styles.duration.forRow(banded));
                } else if (value instanceof Number number) {
                    cell.setCellValue(number.doubleValue());
                    cell.setCellStyle(styles.numeric.forRow(banded));
                } else if (value instanceof Boolean flag) {
                    cell.setCellValue(flag);
                    cell.setCellStyle(styles.text.forRow(banded));
                } else {
                    String text = value instanceof WebLink link ? link.url() : Objects.toString(value, "");
                    if (!text.isEmpty()) cell.setCellValue(text);
                    if (value instanceof WebLink link && link.isClickable()) {
                        var hyperlink = sheet.getWorkbook().getCreationHelper().createHyperlink(HyperlinkType.URL);
                        hyperlink.setAddress(link.url());
                        cell.setHyperlink(hyperlink);
                        cell.setCellStyle(styles.link.forRow(banded));
                    } else {
                        cell.setCellStyle(styles.text.forRow(banded));
                    }
                    int available = Math.max(1, columns[i].width() - 6);
                    int textLines = text.lines().mapToInt(line -> Math.max(1, (line.length() + available - 1) / available)).sum();
                    lines = Math.max(lines, textLines);
                }
            }
            row.setHeightInPoints(Math.min(405, Math.max(21, lines * 14 + 7)));
        }

        boolean hasData() {
            return nextRow > FIRST_DATA_ROW;
        }

        void finish() {
            sheet.setAutoFilter(new CellRangeAddress(HEADER_ROW, Math.max(FIRST_DATA_ROW, nextRow - 1), 0, columns.length - 1));
        }

        void taskControls() {
            int lastRow = Math.max(FIRST_DATA_ROW, nextRow - 1);
            var statuses = Arrays.stream(TaskStatus.values()).map(TaskStatus::getLabel).toArray(String[]::new);
            var priorities = Arrays.stream(Priority.values()).map(Priority::getLabel).toArray(String[]::new);
            dropdown(3, lastRow, statuses);
            dropdown(5, lastRow, priorities);
            color(3, lastRow, "$D4=\"Completed\"", 0xDDF4E7);
            color(3, lastRow, "$D4=\"In progress\"", 0xDEECFF);
            color(3, lastRow, "$D4=\"Blocked/Closed\"", 0xFCE4E6);
            color(5, lastRow, "$F4=\"High\"", 0xFFF1CC);
            color(4, lastRow, "AND(ISNUMBER($E4),$E4<TODAY(),$D4<>\"Completed\",$L4<>TRUE)", 0xFCE4E6);
        }

        private void dropdown(int column, int lastRow, String[] values) {
            var helper = sheet.getDataValidationHelper();
            var rule = helper.createValidation(helper.createExplicitListConstraint(values),
                    new CellRangeAddressList(FIRST_DATA_ROW, lastRow, column, column));
            rule.setSuppressDropDownArrow(false);
            rule.setShowErrorBox(true);
            rule.createErrorBox("Choose a list value", "Select one of the available options.");
            sheet.addValidationData(rule);
        }

        private void color(int column, int lastRow, String formula, int fill) {
            var formatting = sheet.getSheetConditionalFormatting();
            var rule = formatting.createConditionalFormattingRule(formula);
            var pattern = rule.createPatternFormatting();
            pattern.setFillBackgroundColor(rgb(fill));
            pattern.setFillForegroundColor(rgb(fill));
            pattern.setFillPattern(PatternFormatting.SOLID_FOREGROUND);
            formatting.addConditionalFormatting(new CellRangeAddress[]{
                    new CellRangeAddress(FIRST_DATA_ROW, lastRow, column, column)}, rule);
        }
    }

    private static class Styles {
        final XSSFCellStyle title;
        final XSSFCellStyle header;
        final RowStyles text;
        final RowStyles date;
        final RowStyles dateTime;
        final RowStyles duration;
        final RowStyles numeric;
        final RowStyles link;

        Styles(XSSFWorkbook book) {
            var normal = font(book, 10, false, 30, 43, 59);
            title = book.createCellStyle();
            title.setFont(font(book, 16, true, 28, 56, 82));
            title.setVerticalAlignment(VerticalAlignment.CENTER);
            header = book.createCellStyle();
            header.setFont(font(book, 10, true, 255, 255, 255));
            header.setFillForegroundColor(rgb(0x1C3852));
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            text = pair(book, normal, null, true);
            date = pair(book, normal, "dd mmm yyyy", false);
            dateTime = pair(book, normal, "dd mmm yyyy hh:mm", false);
            duration = pair(book, normal, "[h]:mm:ss", false);
            numeric = pair(book, normal, "#,##0", false);
            var hyperlink = font(book, 10, false, 27, 85, 151);
            hyperlink.setUnderline(Font.U_SINGLE);
            link = pair(book, hyperlink, null, true);
        }

        private static org.apache.poi.xssf.usermodel.XSSFFont font(XSSFWorkbook book, int size, boolean bold,
                                                                  int red, int green, int blue) {
            var font = book.createFont();
            font.setFontName("Arial");
            font.setFontHeightInPoints((short) size);
            font.setBold(bold);
            font.setColor(new XSSFColor(new byte[]{(byte) red, (byte) green, (byte) blue}));
            return font;
        }

        private static RowStyles pair(XSSFWorkbook book, Font font, String format, boolean wrap) {
            var plain = book.createCellStyle();
            plain.setFont(font);
            plain.setVerticalAlignment(VerticalAlignment.TOP);
            plain.setWrapText(wrap);
            if (wrap) plain.setIndention((short) 1);
            if (format != null) plain.setDataFormat(book.createDataFormat().getFormat(format));
            var banded = book.createCellStyle();
            banded.cloneStyleFrom(plain);
            banded.setFillForegroundColor(rgb(0xF1F6FB));
            banded.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return new RowStyles(plain, banded);
        }
    }

    private record RowStyles(XSSFCellStyle plain, XSSFCellStyle banded) {
        XSSFCellStyle forRow(boolean useBand) {
            return useBand ? banded : plain;
        }
    }
}
