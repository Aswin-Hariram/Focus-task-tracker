package com.productivity.attachment.service;

import com.productivity.attachment.entity.Attachment;
import com.productivity.attachment.entity.ReferenceLink;
import com.productivity.attachment.repository.AttachmentRepository;
import com.productivity.attachment.repository.ReferenceLinkRepository;
import com.productivity.core.exception.ResourceNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock
    private AttachmentRepository attachmentRepo;

    @Mock
    private ReferenceLinkRepository linkRepo;

    @TempDir
    Path tempDir;

    private AttachmentService service;

    @BeforeEach
    void setUp() {
        service = new AttachmentService(attachmentRepo, linkRepo, tempDir.toString());
    }

    @Test
    void testStoreTaskAttachment() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hello world".getBytes());
        when(attachmentRepo.save(any(Attachment.class))).thenAnswer(i -> {
            Attachment att = i.getArgument(0);
            att.setId(1L);
            return att;
        });

        Attachment stored = service.store(10L, file);
        assertThat(stored.getOriginalName()).isEqualTo("test.txt");
        assertThat(stored.getTaskId()).isEqualTo(10L);
        assertThat(stored.getProgressUpdateId()).isNull();
    }

    @Test
    void testStoreProgressAttachment() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "notes.pdf", "application/pdf", "pdf bytes".getBytes());
        when(attachmentRepo.save(any(Attachment.class))).thenAnswer(i -> {
            Attachment att = i.getArgument(0);
            att.setId(2L);
            return att;
        });

        Attachment stored = service.store(10L, 5L, file);
        assertThat(stored.getOriginalName()).isEqualTo("notes.pdf");
        assertThat(stored.getTaskId()).isEqualTo(10L);
        assertThat(stored.getProgressUpdateId()).isEqualTo(5L);
    }

    @Test
    void storesDiskImagesAndFilesWithoutRecognizedTypes() throws IOException {
        when(attachmentRepo.save(any(Attachment.class))).thenAnswer(call -> call.getArgument(0));
        byte[] bytes = {1, 2, 3};
        var dmg = service.store(10L, new MockMultipartFile("file", "installer.dmg", "application/x-apple-diskimage", bytes));
        var unknown = service.store(10L, new MockMultipartFile("file", "README", "unknown", bytes));

        assertThat(dmg.getOriginalName()).isEqualTo("installer.dmg");
        assertThat(dmg.getContentType()).isEqualTo("application/x-apple-diskimage");
        assertThat(Files.readAllBytes(service.load(dmg).getFile().toPath())).containsExactly(bytes);
        assertThat(unknown.getOriginalName()).isEqualTo("README");
        assertThat(unknown.getContentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void testStoreEmptyFileThrows() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> service.store(10L, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Choose a file to upload");
    }

    @Test
    void testAddValidLink() {
        when(linkRepo.save(any(ReferenceLink.class))).thenAnswer(i -> {
            ReferenceLink link = i.getArgument(0);
            link.setId(100L);
            return link;
        });

        ReferenceLink link = service.addLink(1L, "Docs", "https://google.com");
        assertThat(link.getLabel()).isEqualTo("Docs");
        assertThat(link.getUrl()).isEqualTo("https://google.com");
    }

    @Test
    void testAddInvalidLinkThrows() {
        assertThatThrownBy(() -> service.addLink(1L, "Bad", "ftp://example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URL must start with http:// or https://");
    }

    @Test
    void testDeleteFile() throws IOException {
        Attachment attachment = new Attachment();
        attachment.setId(5L);
        attachment.setTaskId(1L);
        attachment.setStoredName("some-file.txt");
        when(attachmentRepo.findById(5L)).thenReturn(Optional.of(attachment));

        service.deleteFile(1L, 5L);
        verify(attachmentRepo).delete(attachment);
    }

    @Test
    void testDeleteAllForTask() throws IOException {
        Attachment att = new Attachment();
        att.setId(1L);
        att.setTaskId(10L);
        att.setStoredName("task-file.txt");
        when(attachmentRepo.findByTaskIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(att));
        when(linkRepo.findByTaskIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());

        service.deleteAllForTask(10L);
        verify(attachmentRepo).delete(att);
    }

    @Test
    void testStoreCompletionAttachmentAllowsAnyTypeAndLargeSize() throws IOException {
        // Zip file (not in standard ALLOWED list) and size exceeding 25 MB
        byte[] dummyBytes = new byte[100];
        MockMultipartFile zipFile = new MockMultipartFile("file", "project-archive.zip", "application/zip", dummyBytes) {
            @Override
            public long getSize() {
                return 50L * 1024 * 1024; // 50 MB simulated
            }
        };

        when(attachmentRepo.save(any(Attachment.class))).thenAnswer(i -> {
            Attachment att = i.getArgument(0);
            att.setId(99L);
            return att;
        });

        Attachment stored = service.storeCompletionAttachment(10L, zipFile);
        assertThat(stored.getOriginalName()).isEqualTo("project-archive.zip");
        assertThat(stored.getContentType()).isEqualTo("application/zip");
        assertThat(stored.getSizeBytes()).isEqualTo(50L * 1024 * 1024);
    }

    @Test
    void testStoreTaskAttachmentAllowsLargeSizeWithoutLimit() throws IOException {
        MockMultipartFile largePdf = new MockMultipartFile("file", "large-document.pdf", "application/pdf", new byte[10]) {
            @Override
            public long getSize() {
                return 100L * 1024 * 1024; // 100 MB simulated
            }
        };

        when(attachmentRepo.save(any(Attachment.class))).thenAnswer(i -> {
            Attachment att = i.getArgument(0);
            att.setId(101L);
            return att;
        });

        Attachment stored = service.store(10L, largePdf);
        assertThat(stored.getOriginalName()).isEqualTo("large-document.pdf");
        assertThat(stored.getSizeBytes()).isEqualTo(100L * 1024 * 1024);
    }
}
