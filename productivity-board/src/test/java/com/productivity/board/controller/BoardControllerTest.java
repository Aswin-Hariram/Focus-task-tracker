package com.productivity.board.controller;

import com.productivity.board.service.BoardService;
import com.productivity.category.entity.Category;
import com.productivity.category.service.CategoryService;
import com.productivity.task.entity.Task;
import com.productivity.task.enums.TaskStatus;
import com.productivity.task.service.TaskService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardControllerTest {

    @Mock
    private BoardService boardService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private TaskService taskService;

    @InjectMocks
    private BoardController controller;

    private Model model;

    @BeforeEach
    void setUp() {
        model = new ConcurrentModel();
    }

    @Test
    void testBoardRendersWithCategoriesAndTasks() {
        LocalDate date = LocalDate.now();
        Category cat = new Category();
        cat.setId(1L);
        cat.setName("Work");

        Map<TaskStatus, List<Task>> boardMap = Map.of(
                TaskStatus.IN_PROGRESS, List.of(new Task())
        );

        when(categoryService.findActive()).thenReturn(List.of(cat));
        when(boardService.board(date, 1L)).thenReturn(boardMap);

        String view = controller.board(date, 1L, model);
        assertThat(view).isEqualTo("board");
        assertThat(model.getAttribute("pageTitle")).isEqualTo("Daily board");
        assertThat(model.getAttribute("selectedDate")).isEqualTo(date);
        assertThat(model.getAttribute("selectedCategory")).isEqualTo(1L);
        assertThat(model.getAttribute("board")).isEqualTo(boardMap);
        assertThat(model.getAttribute("categories")).isEqualTo(List.of(cat));
    }

    @Test
    void testMoveTaskUpdatesStatusAndReturnsJson() {
        Long taskId = 99L;
        TaskStatus newStatus = TaskStatus.COMPLETED;

        ResponseEntity<Map<String, Object>> response = controller.moveTask(taskId, newStatus, "Done via drag and drop");

        verify(taskService).changeStatus(taskId, newStatus, "Done via drag and drop");
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);
        assertThat(response.getBody().get("id")).isEqualTo(taskId);
        assertThat(response.getBody().get("status")).isEqualTo("COMPLETED");
        assertThat(response.getBody().get("statusLabel")).isEqualTo("Completed");
    }

    @Test
    void testBoardRendersMultipleActiveTimers() {
        LocalDate date = LocalDate.now();
        Category cat = new Category();
        cat.setId(1L);
        cat.setName("Work");

        Task t1 = new Task();
        t1.setId(101L);
        t1.setName("Task 1");
        t1.setCategory(cat);
        t1.setPriority(com.productivity.task.enums.Priority.HIGH);
        t1.setTimerStatus(com.productivity.task.enums.TaskTimerStatus.RUNNING);
        t1.setTimerStartedAt(java.time.LocalDateTime.now().minusSeconds(120));

        Task t2 = new Task();
        t2.setId(102L);
        t2.setName("Task 2");
        t2.setCategory(cat);
        t2.setPriority(com.productivity.task.enums.Priority.MEDIUM);
        t2.setTimerStatus(com.productivity.task.enums.TaskTimerStatus.RUNNING);
        t2.setTimerStartedAt(java.time.LocalDateTime.now().minusSeconds(60));

        when(taskService.getActiveTimers()).thenReturn(List.of(t1, t2));
        when(categoryService.findActive()).thenReturn(List.of(cat));
        when(boardService.board(date, null)).thenReturn(Map.of(TaskStatus.IN_PROGRESS, List.of(t1, t2)));

        String view = controller.board(date, null, model);
        assertThat(view).isEqualTo("board");
        assertThat(model.getAttribute("boardActiveCount")).isEqualTo(2);
        List<?> views = (List<?>) model.getAttribute("boardActiveTimerViews");
        assertThat(views).hasSize(2);
    }
}
