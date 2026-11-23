package com.productivity.overdue.controller;

import com.productivity.category.service.CategoryService;
import com.productivity.overdue.service.OverdueService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Locale;

@Controller
public class OverdueController {
    private final OverdueService overdue;
    private final CategoryService categories;

    public OverdueController(OverdueService overdue, CategoryService categories) {
        this.overdue = overdue;
        this.categories = categories;
    }

    @GetMapping("/overdue")
    public String overdue(@RequestParam(required = false) Long category, Model model) {
        List<OverdueService.OverdueTask> tasks = overdue.find(category);
        double avg = tasks.isEmpty() ? 0.0 : tasks.stream()
                .mapToLong(OverdueService.OverdueTask::daysOverdue)
                .average()
                .orElse(0.0);

        model.addAttribute("pageTitle", "Overdue tasks");
        model.addAttribute("overdueTasks", tasks);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("categories", categories.findActive());
        model.addAttribute("avgDelay", String.format(Locale.US, "%.1f", avg));
        return "overdue";
    }
}
