package com.productivity.progress.repository;

import com.productivity.progress.entity.ProgressUpdate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgressUpdateRepository extends JpaRepository<ProgressUpdate, Long> {
    List<ProgressUpdate> findByTaskIdOrderByRecordedAtDesc(Long taskId);
    List<ProgressUpdate> findByTaskIdAndIterationOrderByRecordedAtDesc(Long taskId, int iteration);
    long countByTaskId(Long taskId);
}
