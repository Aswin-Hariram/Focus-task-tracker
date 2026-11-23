package com.productivity.task.repository;

import com.productivity.task.entity.TaskTimeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TaskTimeLogRepository extends JpaRepository<TaskTimeLog, Long> {
    List<TaskTimeLog> findByTaskIdOrderByStartedAtDesc(Long taskId);
    void deleteByTaskId(Long taskId);
}
