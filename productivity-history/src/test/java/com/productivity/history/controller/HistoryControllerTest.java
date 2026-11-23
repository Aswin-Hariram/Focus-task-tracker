package com.productivity.history.controller;

import com.productivity.category.service.CategoryService;
import com.productivity.task.entity.TaskActivity;
import com.productivity.task.entity.TaskActivity.Action;
import com.productivity.task.enums.TaskStatus;
import com.productivity.task.repository.TaskActivityRepository;
import com.productivity.task.service.TaskService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoryControllerTest {
    @Mock private TaskActivityRepository activity;
    @Mock private CategoryService categories;
    @Mock private TaskService tasks;
    @InjectMocks private HistoryController controller;

    @Test
    void filtersFeedByActionAndShowsFullTaskTimeline() {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(5);
        TaskActivity completed = new TaskActivity(1L, "Plan", 10L, "Work", "#2563EB",
                Action.COMPLETED, TaskStatus.COMPLETED, TaskStatus.IN_PROGRESS, today.atTime(10, 0), null);
        TaskActivity created = new TaskActivity(1L, "Plan", 10L, "Work", "#2563EB",
                Action.CREATED, TaskStatus.YET_TO_START, null, from.atTime(9, 0), null);
        when(activity.search("plan", 10L, from.atStartOfDay(), today.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(completed, created));
        when(activity.findByTaskIdOrderByOccurredAtDescIdDesc(1L)).thenReturn(List.of(completed, created));

        var model = new ConcurrentModel();
        String view = controller.history(" plan ", 10L, from, today, Action.COMPLETED,
                null, "activity", "timeline", model);

        assertThat(view).isEqualTo("history");
        assertThat(model.getAttribute("total")).isEqualTo(2);
        assertThat(model.getAttribute("selected")).isEqualTo(completed);
        assertThat(model.getAttribute("taskEvents")).isEqualTo(List.of(completed, created));
        assertThat(model.getAttribute("earlierCount")).isEqualTo(0L);
        assertThat(model.getAttribute("timeline")).isEqualTo(true);
    }

    @Test
    void handlesReversedDatesWithoutQueryingActivity() {
        LocalDate today = LocalDate.now();
        var model = new ConcurrentModel();

        assertThat(controller.history(null, null, today, today.minusDays(1), null, null,
                "activity", "list", model)).isEqualTo("history");
        assertThat(model.getAttribute("error")).isEqualTo("End date cannot be before the start date");
        assertThat(model.getAttribute("total")).isEqualTo(0);
        verifyNoInteractions(activity);
    }
}
