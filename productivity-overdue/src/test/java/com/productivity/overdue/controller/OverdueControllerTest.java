package com.productivity.overdue.controller;

import com.productivity.category.entity.Category;
import com.productivity.category.service.CategoryService;
import com.productivity.overdue.service.OverdueService;
import com.productivity.task.entity.Task;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OverdueControllerTest {

    @Mock
    private OverdueService overdueService;

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private OverdueController controller;

    private Model model;

    @BeforeEach
    void setUp() {
        model = new ConcurrentModel();
    }

    @Test
    void testOverdueRendersWithTasksAndCategories() {
        Category cat = new Category();
        cat.setId(2L);
        cat.setName("Urgent");

        Task task = new Task();
        task.setId(10L);
        task.setName("Late task");

        OverdueService.OverdueTask overdueTask = new OverdueService.OverdueTask(task, 3);

        when(categoryService.findActive()).thenReturn(List.of(cat));
        when(overdueService.find(2L)).thenReturn(List.of(overdueTask));

        String view = controller.overdue(2L, model);
        assertThat(view).isEqualTo("overdue");
        assertThat(model.getAttribute("pageTitle")).isEqualTo("Overdue tasks");
        assertThat(model.getAttribute("selectedCategory")).isEqualTo(2L);
        assertThat(model.getAttribute("overdueTasks")).isEqualTo(List.of(overdueTask));
        assertThat(model.getAttribute("categories")).isEqualTo(List.of(cat));
    }
}
