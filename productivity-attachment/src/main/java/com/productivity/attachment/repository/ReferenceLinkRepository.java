package com.productivity.attachment.repository;

import com.productivity.attachment.entity.ReferenceLink;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferenceLinkRepository extends JpaRepository<ReferenceLink, Long> {
    List<ReferenceLink> findByTaskIdOrderByCreatedAtDesc(Long taskId);
    List<ReferenceLink> findByTaskIdAndProgressUpdateIdIsNullOrderByCreatedAtDesc(Long taskId);
    List<ReferenceLink> findByProgressUpdateIdOrderByCreatedAtDesc(Long progressUpdateId);
}
