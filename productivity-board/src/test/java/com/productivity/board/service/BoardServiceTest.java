package com.productivity.board.service;

import com.productivity.category.entity.Category;
import com.productivity.task.entity.Task;
import com.productivity.task.enums.TaskStatus;
import com.productivity.task.service.TaskService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock
    private TaskService taskService;

    private BoardService service;

    @BeforeEach
    void setUp() {
        service = new BoardService(taskService);
    }

    @Test
    void testBoardRetrievesTasksGroupedByStatus() {
        LocalDate today = LocalDate.now();

        Category cat1 = new Category();
        cat1.setId(1L);
        cat1.setName("Work");

        Category cat2 = new Category();
        cat2.setId(2L);
        cat2.setName("Personal");

        Task t1 = new Task();
        t1.setId(1L);
        t1.setName("Task 1");
        t1.setStatus(TaskStatus.YET_TO_START);
        t1.setCategory(cat1);

        Task t2 = new Task();
        t2.setId(2L);
        t2.setName("Task 2");
        t2.setStatus(TaskStatus.IN_PROGRESS);
        t2.setCategory(cat1);

        Task t3 = new Task();
        t3.setId(3L);
        t3.setName("Task 3");
        t3.setStatus(TaskStatus.COMPLETED);
        t3.setCategory(cat2);

        when(taskService.forBoard(today)).thenReturn(List.of(t1, t2, t3));

        Map<TaskStatus, List<Task>> resultAll = service.board(today, null);
        assertThat(resultAll.get(TaskStatus.YET_TO_START)).containsExactly(t1);
        assertThat(resultAll.get(TaskStatus.IN_PROGRESS)).containsExactly(t2);
        assertThat(resultAll.get(TaskStatus.COMPLETED)).containsExactly(t3);

        Map<TaskStatus, List<Task>> resultFiltered = service.board(today, 1L);
        assertThat(resultFiltered.get(TaskStatus.YET_TO_START)).containsExactly(t1);
        assertThat(resultFiltered.get(TaskStatus.IN_PROGRESS)).containsExactly(t2);
        assertThat(resultFiltered.get(TaskStatus.COMPLETED)).isEmpty();
    }
}
