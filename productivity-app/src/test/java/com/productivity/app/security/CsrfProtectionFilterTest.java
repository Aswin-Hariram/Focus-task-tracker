package com.productivity.app.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.storage.default-location=",
        "spring.datasource.url=jdbc:h2:mem:csrf-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.seed-demo-data=false",
        "app.storage.root=target/test-csrf-storage",
        "app.security.csrf.enabled=true"
})
class CsrfProtectionFilterTest {

    @Autowired
    MockMvc mvc;

    @Test
    void postWithoutCsrfTokenIsRejectedWith403() throws Exception {
        mvc.perform(post("/tasks/save")
                        .param("name", "Malicious forged task"))
                .andExpect(status().isForbidden());
    }

    @Test
    void postWithValidCsrfTokenIsProcessed() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // Initial GET to populate session with CSRF token
        MvcResult getResult = mvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andReturn();

        String token = (String) getResult.getRequest().getAttribute(CsrfProtectionFilter.CSRF_ATTRIBUTE);
        assertThat(token).isNotBlank();

        // POST using header
        mvc.perform(post("/tasks/999999/status")
                        .session(session)
                        .header(CsrfProtectionFilter.CSRF_HEADER, token)
                        .param("status", "IN_PROGRESS"))
                // Expect 404 because task 999999 doesn't exist, which proves CSRF filter passed and reached controller!
                .andExpect(status().isNotFound());
    }
}
