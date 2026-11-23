package com.productivity.attachment.service;

import com.productivity.attachment.entity.Attachment;
import com.productivity.attachment.entity.ReferenceLink;
import com.productivity.attachment.repository.AttachmentRepository;
import com.productivity.attachment.repository.ReferenceLinkRepository;
import com.productivity.core.common.AppConstants;
import com.productivity.core.exception.ResourceNotFoundException;
import com.productivity.core.security.SecurityUtils;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class AttachmentService {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AttachmentService.class);

    private final AttachmentRepository attachments;
    private final ReferenceLinkRepository links;
    private final Path taskStorageRoot;
    private final Path progressStorageRoot;
    private final Path exportsStorageRoot;

    public AttachmentService(AttachmentRepository attachments, ReferenceLinkRepository links,
                             @Value("${app.storage.root:./storage}") String storageRoot) {
        this.attachments = attachments;
        this.links = links;
        Path base = Path.of(storageRoot).toAbsolutePath().normalize();
        this.taskStorageRoot = base.resolve(AppConstants.TASK_ATTACHMENTS_DIR);
        this.progressStorageRoot = base.resolve(AppConstants.PROGRESS_ATTACHMENTS_DIR);
        this.exportsStorageRoot = base.resolve(AppConstants.EXPORTS_DIR);
        initDirectories();
    }

    private void initDirectories() {
        try {
            Files.createDirectories(taskStorageRoot);
            Files.createDirectories(progressStorageRoot);
            Files.createDirectories(exportsStorageRoot);
        } catch (IOException ignored) {}
    }

    public List<Attachment> filesForTask(Long taskId) { return attachments.findByTaskIdAndProgressUpdateIdIsNullOrderByCreatedAtDesc(taskId); }
    public List<Attachment> allFilesForTask(Long taskId) { return attachments.findByTaskIdOrderByCreatedAtDesc(taskId); }
    public List<Attachment> filesForProgress(Long progressUpdateId) { return attachments.findByProgressUpdateIdOrderByCreatedAtDesc(progressUpdateId); }
    public List<ReferenceLink> linksForTask(Long taskId) { return links.findByTaskIdAndProgressUpdateIdIsNullOrderByCreatedAtDesc(taskId); }
    public List<ReferenceLink> linksForProgress(Long progressUpdateId) { return links.findByProgressUpdateIdOrderByCreatedAtDesc(progressUpdateId); }
    public Attachment get(Long id) { return attachments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Attachment not found")); }
    public ReferenceLink getLink(Long id) { return links.findById(id).orElseThrow(() -> new ResourceNotFoundException("Reference link not found")); }

    @Transactional
    public Attachment store(Long taskId, MultipartFile file) throws IOException {
        return store(taskId, null, file);
    }

    @Transactional
    public Attachment storeCompletionAttachment(Long taskId, MultipartFile file) throws IOException {
        return store(taskId, null, file);
    }

    @Transactional
    public Attachment storeProgressAttachment(Long taskId, Long progressUpdateId, MultipartFile file) throws IOException {
        return store(taskId, progressUpdateId, file);
    }

    @Transactional
    public Attachment store(Long taskId, Long progressUpdateId, MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("Choose a file to upload");
        String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String suppliedType = file.getContentType();
        if (suppliedType != null && !suppliedType.isBlank()) {
            try {
                MediaType parsed = MediaType.parseMediaType(suppliedType);
                if (parsed.isConcrete()) contentType = parsed.getType() + "/" + parsed.getSubtype();
            } catch (InvalidMediaTypeException ignored) {
                // The original file can still be downloaded as binary data.
            }
        }
        if (contentType.length() > 120) contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String original = SecurityUtils.sanitizeFilename(file.getOriginalFilename());

        Path targetDir = progressUpdateId != null ? progressStorageRoot : taskStorageRoot;
        Files.createDirectories(targetDir);

        String extension = original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
        String stored = UUID.randomUUID() + extension.toLowerCase();
        Path targetPath = SecurityUtils.validatePathWithinRoot(targetDir.resolve(stored), targetDir);
        file.transferTo(targetPath);

        Attachment attachment = new Attachment();
        attachment.setTaskId(taskId);
        attachment.setProgressUpdateId(progressUpdateId);
        attachment.setOriginalName(original);
        attachment.setStoredName(stored);
        attachment.setContentType(contentType);
        attachment.setSizeBytes(file.getSize());
        return attachments.save(attachment);
    }

    public Resource load(Attachment attachment) throws IOException {
        Path taskPath = SecurityUtils.validatePathWithinRoot(taskStorageRoot.resolve(attachment.getStoredName()), taskStorageRoot);
        Path progressPath = SecurityUtils.validatePathWithinRoot(progressStorageRoot.resolve(attachment.getStoredName()), progressStorageRoot);

        Path resolved = null;
        if (Files.isRegularFile(taskPath)) {
            resolved = taskPath;
        } else if (Files.isRegularFile(progressPath)) {
            resolved = progressPath;
        }

        if (resolved == null) {
            throw new ResourceNotFoundException("Attachment file not found");
        }
        return new UrlResource(resolved.toUri());
    }

    @Transactional
    public void deleteFile(Long taskId, Long id) throws IOException {
        Attachment attachment = get(id);
        if (!attachment.getTaskId().equals(taskId)) throw new IllegalArgumentException("Attachment does not belong to this task");
        Path taskPath = SecurityUtils.validatePathWithinRoot(taskStorageRoot.resolve(attachment.getStoredName()), taskStorageRoot);
        Path progressPath = SecurityUtils.validatePathWithinRoot(progressStorageRoot.resolve(attachment.getStoredName()), progressStorageRoot);
        Files.deleteIfExists(taskPath);
        Files.deleteIfExists(progressPath);
        attachments.delete(attachment);
    }

    @Transactional
    public void deleteAllForTask(Long taskId) throws IOException {
        List<Attachment> taskFiles = allFilesForTask(taskId);
        for (Attachment att : taskFiles) {
            Path taskPath = SecurityUtils.validatePathWithinRoot(taskStorageRoot.resolve(att.getStoredName()), taskStorageRoot);
            Path progressPath = SecurityUtils.validatePathWithinRoot(progressStorageRoot.resolve(att.getStoredName()), progressStorageRoot);
            Files.deleteIfExists(taskPath);
            Files.deleteIfExists(progressPath);
            attachments.delete(att);
        }
        List<ReferenceLink> taskLinks = links.findByTaskIdOrderByCreatedAtDesc(taskId);
        links.deleteAll(taskLinks);
    }

    @Transactional
    public void deleteAllForProgress(Long progressUpdateId) {
        List<Attachment> progressFiles = filesForProgress(progressUpdateId);
        List<Path> paths = new java.util.ArrayList<>();
        for (Attachment file : progressFiles) {
            paths.add(SecurityUtils.validatePathWithinRoot(taskStorageRoot.resolve(file.getStoredName()), taskStorageRoot));
            paths.add(SecurityUtils.validatePathWithinRoot(progressStorageRoot.resolve(file.getStoredName()), progressStorageRoot));
        }
        attachments.deleteAll(progressFiles);
        links.deleteAll(linksForProgress(progressUpdateId));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (Path path : paths) {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ex) {
                        LOG.warn("Could not remove a deleted progress attachment", ex);
                    }
                }
            }
        });
    }

    public Path saveExportFile(String filename, byte[] content) throws IOException {
        Files.createDirectories(exportsStorageRoot);
        String safeName = SecurityUtils.sanitizeFilename(filename);
        Path target = SecurityUtils.validatePathWithinRoot(exportsStorageRoot.resolve(safeName), exportsStorageRoot);
        Files.write(target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return target;
    }

    @Transactional
    public ReferenceLink addLink(Long taskId, String label, String url) {
        return addProgressLink(taskId, null, label, url);
    }

    @Transactional
    public ReferenceLink addProgressLink(Long taskId, Long progressUpdateId, String label, String url) {
        ReferenceLink link = validatedLink(label, url);
        link.setTaskId(taskId);
        link.setProgressUpdateId(progressUpdateId);
        return links.save(link);
    }

    @Transactional
    public void editProgressLink(Long taskId, Long progressId, Long linkId, String label, String url) {
        ReferenceLink link = getLink(linkId);
        if (!taskId.equals(link.getTaskId()) || !progressId.equals(link.getProgressUpdateId())) {
            throw new IllegalArgumentException("Reference link does not belong to this progress update");
        }
        ReferenceLink values = validatedLink(label, url);
        link.setLabel(values.getLabel());
        link.setUrl(values.getUrl());
    }

    public void validateLink(String label, String url) {
        validatedLink(label, url);
    }

    private ReferenceLink validatedLink(String label, String url) {
        URI uri;
        try { uri = URI.create(url.trim()); } catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Enter a valid URL"); }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
            throw new IllegalArgumentException("URL must start with http:// or https://");
        }
        String normalizedLabel = label == null || label.isBlank() ? uri.getHost() : label.trim();
        if (normalizedLabel.length() > 120) throw new IllegalArgumentException("Link label must be 120 characters or fewer");
        if (uri.toString().length() > 1000) throw new IllegalArgumentException("URL must be 1,000 characters or fewer");
        ReferenceLink link = new ReferenceLink();
        link.setLabel(normalizedLabel);
        link.setUrl(uri.toString());
        return link;
    }

    @Transactional
    public void deleteLink(Long taskId, Long id) {
        ReferenceLink link = getLink(id);
        if (!link.getTaskId().equals(taskId)) throw new IllegalArgumentException("Reference link does not belong to this task");
        links.delete(link);
    }
}
