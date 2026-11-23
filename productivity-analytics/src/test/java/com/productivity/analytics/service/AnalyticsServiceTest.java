package com.productivity.analytics.service;

import com.productivity.task.entity.Task;
import com.productivity.task.enums.TaskStatus;
import com.productivity.task.repository.TaskRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private TaskRepository tasks;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(tasks);
    }

    @Test
    void testSummaryCalculations() {
        when(tasks.countByArchivedFalse()).thenReturn(10L);
        when(tasks.countByStatusAndArchivedFalse(TaskStatus.COMPLETED)).thenReturn(6L);
        when(tasks.countByStatusAndArchivedFalse(TaskStatus.IN_PROGRESS)).thenReturn(2L);
        when(tasks.countByStatusAndArchivedFalse(TaskStatus.YET_TO_START)).thenReturn(2L);
        when(tasks.findByDeadlineBeforeAndStatusNotAndArchivedFalseOrderByDeadlineAsc(any(LocalDate.class), eq(TaskStatus.COMPLETED)))
                .thenReturn(List.of(new Task()));

        AnalyticsService.Summary summary = service.summary();
        assertThat(summary.total()).isEqualTo(10L);
        assertThat(summary.completed()).isEqualTo(6L);
        assertThat(summary.inProgress()).isEqualTo(2L);
        assertThat(summary.pending()).isEqualTo(2L);
        assertThat(summary.overdue()).isEqualTo(1L);
        assertThat(summary.completionRate()).isEqualTo(60);
    }

    @Test
    void testCategoryDistribution() {
        List<Object[]> rows = List.<Object[]>of(
                new Object[]{"Work", 5L},
                new Object[]{"Personal", 3L}
        );
        when(tasks.countByCategory()).thenReturn(rows);

        Map<String, Long> dist = service.categoryDistribution();
        assertThat(dist).containsEntry("Work", 5L).containsEntry("Personal", 3L);
    }

    @Test
    void testCategorySummaries() {
        List<Object[]> rows = List.<Object[]>of(
                new Object[]{"Work", 4L, 2L}
        );
        when(tasks.completionByCategory()).thenReturn(rows);

        List<AnalyticsService.CategorySummary> summaries = service.categorySummaries();
        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).category()).isEqualTo("Work");
        assertThat(summaries.get(0).total()).isEqualTo(4L);
        assertThat(summaries.get(0).completed()).isEqualTo(2L);
        assertThat(summaries.get(0).completionRate()).isEqualTo(50);
    }
}
