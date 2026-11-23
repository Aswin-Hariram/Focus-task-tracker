package com.productivity.category.controller;

import com.productivity.category.entity.Category;
import com.productivity.category.service.CategoryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/categories")
public class CategoryController {
    private final CategoryService categories;
    public CategoryController(CategoryService categories) { this.categories = categories; }

    @GetMapping
    public String list(@RequestParam(required = false) Long edit,
                       @RequestParam(required = false, defaultValue = "all") String filter,
                       Model model) {
        model.addAttribute("pageTitle", "Categories");
        List<Category> allList = categories.findAll();
        List<Category> displayedList;
        if ("active".equalsIgnoreCase(filter)) {
            displayedList = allList.stream().filter(c -> !c.isArchived()).toList();
        } else if ("archived".equalsIgnoreCase(filter)) {
            displayedList = allList.stream().filter(Category::isArchived).toList();
        } else {
            displayedList = allList;
        }

        model.addAttribute("categories", displayedList);
        model.addAttribute("filter", filter);

        if (edit != null) {
            try {
                Category toEdit = categories.get(edit);
                model.addAttribute("categoryForm", toEdit);
                model.addAttribute("isEditing", true);
            } catch (Exception ignored) {
                if (!model.containsAttribute("categoryForm")) {
                    model.addAttribute("categoryForm", new Category());
                    model.addAttribute("isEditing", false);
                }
            }
        } else if (!model.containsAttribute("categoryForm")) {
            model.addAttribute("categoryForm", new Category());
            model.addAttribute("isEditing", false);
        }
        return "categories";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("categoryForm") Category category, BindingResult result,
                       Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("categories", categories.findAll());
            model.addAttribute("pageTitle", "Categories");
            model.addAttribute("isEditing", category.getId() != null);
            return "categories";
        }
        try {
            if (category.getId() != null) {
                categories.update(category.getId(), category.getName(), category.getColor());
                redirect.addFlashAttribute("success", "Category updated");
            } else {
                categories.save(category);
                redirect.addFlashAttribute("success", "Category saved");
            }
        } catch (IllegalArgumentException ex) {
            result.rejectValue("name", "duplicate", ex.getMessage());
            model.addAttribute("categories", categories.findAll());
            model.addAttribute("pageTitle", "Categories");
            model.addAttribute("isEditing", category.getId() != null);
            return "categories";
        }
        return "redirect:/categories";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes redirect) {
        categories.toggleArchived(id);
        redirect.addFlashAttribute("success", "Category status updated");
        return "redirect:/categories";
    }
}
