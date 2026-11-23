package com.productivity.overdue.service;

import com.productivity.category.entity.Category;
import com.productivity.task.entity.Task;
import com.productivity.task.enums.TaskStatus;
import com.productivity.task.service.TaskService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OverdueServiceTest {

    @Mock
    private TaskService taskService;

    private OverdueService service;

    @BeforeEach
    void setUp() {
        service = new OverdueService(taskService);
    }

    @Test
    void testFindCalculatesDaysOverdueAndFilters() {
        LocalDate now = LocalDate.now();

        Category cat1 = new Category();
        cat1.setId(1L);

        Category cat2 = new Category();
        cat2.setId(2L);

        Task t1 = new Task();
        t1.setId(1L);
        t1.setName("Overdue Task 1");
        t1.setCategory(cat1);
        t1.setDeadline(now.minusDays(5));
        t1.setStatus(TaskStatus.IN_PROGRESS);

        Task t2 = new Task();
        t2.setId(2L);
        t2.setName("Overdue Task 2");
        t2.setCategory(cat2);
        t2.setDeadline(now.minusDays(2));
        t2.setStatus(TaskStatus.YET_TO_START);

        when(taskService.overdue()).thenReturn(List.of(t1, t2));

        List<OverdueService.OverdueTask> all = service.find(null);
        assertThat(all).hasSize(2);
        assertThat(all.get(0).daysOverdue()).isEqualTo(5);
        assertThat(all.get(1).daysOverdue()).isEqualTo(2);

        List<OverdueService.OverdueTask> filtered = service.find(1L);
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).task().getId()).isEqualTo(1L);
    }
}
