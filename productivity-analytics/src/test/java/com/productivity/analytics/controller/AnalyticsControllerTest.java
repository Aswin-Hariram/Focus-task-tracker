package com.productivity.analytics.controller;

import com.productivity.analytics.service.AnalyticsService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    @Mock
    private AnalyticsService analyticsService;

    @InjectMocks
    private AnalyticsController controller;

    private Model model;

    @BeforeEach
    void setUp() {
        model = new ConcurrentModel();
    }

    @Test
    void testAnalyticsView() {
        var report = new AnalyticsService.Report(0, 0, 0, 0, 0, "0%", "0%", 0, false,
                List.of(), 4, "", List.of(), "", "", 0);
        when(analyticsService.report(any(), any(), any(), any(), any())).thenReturn(report);

        String view = controller.analytics(model, "week", null, null);
        assertThat(view).isEqualTo("analytics");
        assertThat(model.getAttribute("pageTitle")).isEqualTo("Analytics");
        assertThat(model.getAttribute("report")).isEqualTo(report);
        assertThat(model.getAttribute("period")).isEqualTo("week");
    }
}
