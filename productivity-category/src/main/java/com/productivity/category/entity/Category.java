package com.productivity.category.entity;

import com.productivity.core.entity.AuditableEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "categories")
public class Category extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Enter a category name")
    @Size(max = 60, message = "Use 60 characters or fewer")
    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @NotBlank(message = "Choose a category color")
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Use a six-digit hex color")
    @Column(nullable = false, length = 7)
    private String color = "#2563EB";

    @Column(nullable = false)
    private boolean archived;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name == null ? null : name.trim(); }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
}
