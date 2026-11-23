package com.productivity.attachment.repository;

import com.productivity.attachment.entity.Attachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByTaskIdOrderByCreatedAtDesc(Long taskId);
    List<Attachment> findByTaskIdAndProgressUpdateIdIsNullOrderByCreatedAtDesc(Long taskId);
    List<Attachment> findByProgressUpdateIdOrderByCreatedAtDesc(Long progressUpdateId);
    long countByTaskId(Long taskId);
}
