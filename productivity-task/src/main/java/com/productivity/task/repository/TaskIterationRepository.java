package com.productivity.task.repository;

import com.productivity.task.entity.TaskIteration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskIterationRepository extends JpaRepository<TaskIteration, Long> {
    List<TaskIteration> findByTaskIdOrderByIterationNumberAsc(Long taskId);
    Optional<TaskIteration> findByTaskIdAndIterationNumber(Long taskId, int iterationNumber);
    void deleteByTaskId(Long taskId);
}
