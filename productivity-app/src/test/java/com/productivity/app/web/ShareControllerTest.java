package com.productivity.app.web;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.storage.default-location=",
        "spring.datasource.url=jdbc:h2:mem:productivity-share-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.seed-demo-data=false",
        "app.storage.root=target/test-storage-share",
        "app.security.csrf.enabled=false",
        "app.security.rate-limit.enabled=false"
})
class ShareControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoSpyBean
    private ShareController share;

    @TempDir
    private Path releaseDirectory;

    @BeforeEach
    void createReleaseFixtures() throws Exception {
        Files.createDirectory(releaseDirectory.resolve("mac"));
        Files.writeString(releaseDirectory.resolve("mac/Focus-1.0.0.dmg"), "DMG fixture");
        Files.writeString(releaseDirectory.resolve("focus.jar"), "JAR fixture");
        doReturn(releaseDirectory).when(share).getBinDirectory();
    }

    @Test
    void testShareHubPageRenders() throws Exception {
        mvc.perform(get("/share"))
                .andExpect(status().isOk())
                .andExpect(view().name("share"))
                .andExpect(model().attributeExists("shareUrl"))
                .andExpect(model().attributeExists("releases"))
                .andExpect(content().string(containsString("Share Focus App")))
                .andExpect(content().string(containsString("primaryShareInput")))
                .andExpect(content().string(containsString("Copy link")));
    }

    @Test
    void testDownloadLandingPageRenders() throws Exception {
        mvc.perform(get("/download"))
                .andExpect(status().isOk())
                .andExpect(view().name("download"))
                .andExpect(model().attributeExists("releases"))
                .andExpect(content().string(containsString("Download Focus Fresh")))
                .andExpect(content().string(containsString("Focus for macOS")))
                .andExpect(content().string(containsString("Focus for Windows")))
                .andExpect(content().string(containsString("Focus for Linux")))
                .andExpect(content().string(containsString("Step-by-Step macOS Setup Instructions")))
                .andExpect(content().string(containsString("data-theme-toggle")))
                .andExpect(content().string(containsString("focus-theme")))
                .andExpect(content().string(containsString("data-theme=\"dark\"")));
    }

    @Test
    void testSettingsIncludesShareSection() throws Exception {
        mvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(view().name("settings"))
                .andExpect(content().string(containsString("Share Focus app")))
                .andExpect(content().string(containsString("/share")))
                .andExpect(content().string(containsString("/download")));
    }

    @Test
    void testSidebarIncludesShareLink() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("sidebar-share")))
                .andExpect(content().string(containsString("Share app")));
    }

    @Test
    void testDownloadMacDmgBinary() throws Exception {
        mvc.perform(get("/download/file/mac-dmg"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("Focus-1.0.0.dmg")))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/x-apple-diskimage")));
    }

    @Test
    void testDownloadWindowsSetupBinaryReturnsNotFound() throws Exception {
        mvc.perform(get("/download/file/win-setup"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDownloadWindowsZipBinaryReturnsNotFound() throws Exception {
        mvc.perform(get("/download/file/win-zip"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDownloadExecutableJar() throws Exception {
        mvc.perform(get("/download/file/jar"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("focus.jar")))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/java-archive")));
    }

    @Test
    void testDownloadInvalidKeyReturns404() throws Exception {
        mvc.perform(get("/download/file/invalid-key-xyz"))
                .andExpect(status().isNotFound());
    }
}
