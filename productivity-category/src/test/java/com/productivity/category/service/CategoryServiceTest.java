package com.productivity.category.service;

import com.productivity.category.entity.Category;
import com.productivity.category.repository.CategoryRepository;
import com.productivity.core.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository repository;

    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(repository);
    }

    @Test
    void testFindAllAndActive() {
        Category cat = new Category();
        cat.setName("Work");
        when(repository.findAllByOrderByArchivedAscNameAsc()).thenReturn(List.of(cat));
        when(repository.findByArchivedFalseOrderByNameAsc()).thenReturn(List.of(cat));

        assertThat(service.findAll()).hasSize(1);
        assertThat(service.findActive()).hasSize(1);
    }

    @Test
    void testGetFoundAndNotFound() {
        Category cat = new Category();
        cat.setName("Work");
        when(repository.findById(1L)).thenReturn(Optional.of(cat));
        when(repository.findById(2L)).thenReturn(Optional.empty());

        assertThat(service.get(1L).getName()).isEqualTo("Work");
        assertThatThrownBy(() -> service.get(2L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void testSaveDuplicateNameThrows() {
        Category existing = new Category();
        existing.setId(1L);
        existing.setName("Work");
        when(repository.findByNameIgnoreCase("Work")).thenReturn(Optional.of(existing));

        Category newCat = new Category();
        newCat.setName("Work");

        assertThatThrownBy(() -> service.save(newCat))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void testUpdateCategorySuccess() {
        Category cat = new Category();
        cat.setName("Personal");
        cat.setColor("#000000");
        when(repository.findById(1L)).thenReturn(Optional.of(cat));
        when(repository.findByNameIgnoreCase("Health")).thenReturn(Optional.empty());
        when(repository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Category updated = service.update(1L, "Health", "#2563EB");
        assertThat(updated.getName()).isEqualTo("Health");
        assertThat(updated.getColor()).isEqualTo("#2563EB");
    }

    @Test
    void testToggleArchived() {
        Category cat = new Category();
        cat.setArchived(false);
        when(repository.findById(1L)).thenReturn(Optional.of(cat));

        service.toggleArchived(1L);
        assertThat(cat.isArchived()).isTrue();

        service.toggleArchived(1L);
        assertThat(cat.isArchived()).isFalse();
    }
}
