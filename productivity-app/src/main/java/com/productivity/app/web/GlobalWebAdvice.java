package com.productivity.app.web;

import com.productivity.core.exception.ResourceNotFoundException;
import com.productivity.core.exception.StorageException;
import com.productivity.core.exception.ValidationException;
import com.productivity.task.entity.Task;
import com.productivity.task.enums.TaskTimerStatus;
import com.productivity.task.repository.TaskRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalWebAdvice {

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Autowired(required = false)
    private TaskRepository taskRepository;

    @ModelAttribute
    public void common(Model model, HttpServletRequest request) {
        model.addAttribute("currentPath", request.getRequestURI());
        model.addAttribute("today", LocalDate.now());
        if (applicationContext != null) {
            model.addAttribute("hasBoardModule", applicationContext.containsBean("boardController"));
            model.addAttribute("hasAnalyticsModule", applicationContext.containsBean("analyticsController"));
            model.addAttribute("hasOverdueModule", applicationContext.containsBean("overdueController"));
            model.addAttribute("hasHistoryModule", applicationContext.containsBean("historyController"));
        }
        if (taskRepository != null) {
            List<Task> activeRunningTasks = taskRepository.findByTimerStatus(TaskTimerStatus.RUNNING);
            model.addAttribute("activeRunningTasks", activeRunningTasks);
            model.addAttribute("activeRunningCount", activeRunningTasks.size());
            model.addAttribute("activeTimerTask", activeRunningTasks.isEmpty() ? null : activeRunningTasks.get(0));
        }
        Object csrfToken = request.getAttribute("_csrf");
        if (csrfToken != null) {
            model.addAttribute("_csrf", csrfToken);
        }
    }

    @ExceptionHandler({ResourceNotFoundException.class, NoResourceFoundException.class, NoHandlerFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(Exception exception, HttpServletRequest request, Model model) {
        populateCommon(model, request);
        model.addAttribute("pageTitle", "Page not found");
        String message = (exception instanceof ResourceNotFoundException)
                ? exception.getMessage()
                : "The requested page does not exist.";
        model.addAttribute("message", message);
        return "error/404";
    }

    @ExceptionHandler({IllegalArgumentException.class, ValidationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String badRequest(Exception exception, HttpServletRequest request, Model model) {
        populateCommon(model, request);
        model.addAttribute("pageTitle", "Invalid request");
        model.addAttribute("message", exception.getMessage());
        return "error";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String fileTooLarge(MaxUploadSizeExceededException exception, HttpServletRequest request, Model model) {
        populateCommon(model, request);
        model.addAttribute("pageTitle", "File too large");
        model.addAttribute("message", "The uploaded file exceeds the allowed size limit.");
        return "error";
    }

    @ExceptionHandler(StorageException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String storageError(StorageException exception, HttpServletRequest request, Model model) {
        populateCommon(model, request);
        model.addAttribute("pageTitle", "Storage error");
        model.addAttribute("message", exception.getMessage());
        return "error";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleException(Exception exception, HttpServletRequest request, Model model) {
        populateCommon(model, request);
        model.addAttribute("pageTitle", "Request error");
        org.slf4j.LoggerFactory.getLogger(GlobalWebAdvice.class)
                .error("Unexpected error processing request [{}]: {}", request != null ? request.getRequestURI() : "", exception.getMessage(), exception);
        model.addAttribute("message", "An unexpected error occurred while processing your request. Please try again or return to dashboard.");
        return "error";
    }

    private void populateCommon(Model model, HttpServletRequest request) {
        if (!model.containsAttribute("currentPath")) {
            model.addAttribute("currentPath", request != null ? request.getRequestURI() : "");
        }
        if (!model.containsAttribute("today")) {
            model.addAttribute("today", LocalDate.now());
        }
    }
}
