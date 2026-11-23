package com.productivity.category.controller;

import com.productivity.category.entity.Category;
import com.productivity.category.service.CategoryService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private CategoryController controller;

    private Model model;

    @BeforeEach
    void setUp() {
        model = new ConcurrentModel();
    }

    @Test
    void testListCategories() {
        Category active = new Category();
        active.setId(1L);
        active.setName("Active Cat");
        active.setArchived(false);

        Category archived = new Category();
        archived.setId(2L);
        archived.setName("Archived Cat");
        archived.setArchived(true);

        when(categoryService.findAll()).thenReturn(List.of(active, archived));

        String view = controller.list(null, "active", model);
        assertThat(view).isEqualTo("categories");
        @SuppressWarnings("unchecked")
        List<Category> list = (List<Category>) model.getAttribute("categories");
        assertThat(list).containsExactly(active);
    }

    @Test
    void testSaveNewCategory() {
        Category newCat = new Category();
        newCat.setName("Personal");
        newCat.setColor("#3B82F6");

        BindingResult result = new BeanPropertyBindingResult(newCat, "categoryForm");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.save(newCat, result, model, redirect);
        assertThat(view).isEqualTo("redirect:/categories");
        verify(categoryService).save(newCat);
        assertThat(redirect.getFlashAttributes()).containsKey("success");
    }

    @Test
    void testToggleArchived() {
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.toggle(5L, redirect);
        assertThat(view).isEqualTo("redirect:/categories");
        verify(categoryService).toggleArchived(5L);
    }
}
