package com.productivity.category.service;

import com.productivity.category.entity.Category;
import com.productivity.category.repository.CategoryRepository;
import com.productivity.core.exception.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository repository;

    public CategoryService(CategoryRepository repository) { this.repository = repository; }

    public List<Category> findAll() { return repository.findAllByOrderByArchivedAscNameAsc(); }
    public List<Category> findActive() { return repository.findByArchivedFalseOrderByNameAsc(); }
    public Category get(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    @Transactional
    public Category save(Category category) {
        repository.findByNameIgnoreCase(category.getName())
                .filter(existing -> !java.util.Objects.equals(existing.getId(), category.getId()))
                .ifPresent(existing -> { throw new IllegalArgumentException("A category with this name already exists"); });
        return repository.save(category);
    }

    @Transactional
    public Category update(Long id, String name, String color) {
        Category category = get(id);
        String trimmedName = name == null ? "" : name.trim();
        repository.findByNameIgnoreCase(trimmedName)
                .filter(existing -> !java.util.Objects.equals(existing.getId(), id))
                .ifPresent(existing -> { throw new IllegalArgumentException("A category with this name already exists"); });
        category.setName(trimmedName);
        if (color != null && !color.isBlank()) {
            category.setColor(color.trim());
        }
        return repository.save(category);
    }

    @Transactional
    public void toggleArchived(Long id) {
        Category category = get(id);
        category.setArchived(!category.isArchived());
    }
}
