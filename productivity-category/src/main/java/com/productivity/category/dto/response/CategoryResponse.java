package com.productivity.category.dto.response;

import com.productivity.category.entity.Category;
import java.time.LocalDateTime;

public record CategoryResponse(
        Long id,
        String name,
        String color,
        boolean archived,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CategoryResponse from(Category category) {
        if (category == null) return null;
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getColor(),
                category.isArchived(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}
