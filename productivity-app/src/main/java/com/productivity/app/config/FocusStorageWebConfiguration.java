package com.productivity.app.config;

import com.productivity.app.service.FocusStorage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.support.RequestContextUtils;

@Configuration
public class FocusStorageWebConfiguration implements WebMvcConfigurer, HandlerInterceptor {
    private static final Logger LOG = LoggerFactory.getLogger(FocusStorageWebConfiguration.class);
    private static final Pattern TASK_REDIRECT = Pattern.compile("redirect:/tasks/(\\d+)");
    private final FocusStorage storage;

    public FocusStorageWebConfiguration(FocusStorage storage) {
        this.storage = storage;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/tasks/**", "/board/tasks/**", "/settings/data/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = requestPath(request);
        if (path.startsWith("/settings/data/")) {
            if ("GET".equals(request.getMethod())) sync(request, null);
        } else {
            Long id = taskId(request);
            if (id != null && !path.contains("/timer")) sync(request, id);
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView view) {
        if (!"POST".equals(request.getMethod()) || response.getStatus() >= 400) return;
        String path = requestPath(request);
        if (path.contains("/timer")) return;
        if (path.startsWith("/settings/data/") || path.endsWith("/undo-delete")) {
            sync(request, null);
            return;
        }
        String viewName = view == null ? null : view.getViewName();
        var redirect = TASK_REDIRECT.matcher(viewName == null ? "" : viewName);
        Long id = taskId(request);
        if (id != null) sync(request, id);
        if (redirect.matches()) {
            long redirectId = Long.parseLong(redirect.group(1));
            if (id == null || id != redirectId) sync(request, redirectId);
        }
    }

    private void sync(HttpServletRequest request, Long id) {
        try {
            if (id == null) storage.syncAll();
            else storage.sync(id);
        } catch (IOException ex) {
            LOG.warn("Could not synchronize Focus storage", ex);
            if ("POST".equals(request.getMethod())) {
                var flash = RequestContextUtils.getOutputFlashMap(request);
                if (flash != null) flash.putIfAbsent("error", "Could not sync the selected Focus folder. Check the location in Settings.");
            }
        }
    }

    private static String requestPath(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private static Long taskId(HttpServletRequest request) {
        if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables) {
            Object id = variables.containsKey("taskId") ? variables.get("taskId") : variables.get("id");
            if (id instanceof String value && value.matches("[0-9]{1,18}")) return Long.valueOf(value);
        }
        return null;
    }
}
