package com.productivity.category.repository;

import com.productivity.category.entity.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByOrderByArchivedAscNameAsc();
    List<Category> findByArchivedFalseOrderByNameAsc();
    Optional<Category> findByNameIgnoreCase(String name);
}
