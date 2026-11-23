package com.productivity.task.repository;

import com.productivity.task.entity.TaskActivity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TaskActivityRepository extends JpaRepository<TaskActivity, Long> {
    @Query("""
            select a from TaskActivity a
            where a.occurredAt >= :start and a.occurredAt < :end
              and (:category is null or a.categoryId = :category)
              and (locate(lower(:query), lower(a.taskName)) > 0
                   or locate(lower(:query), lower(coalesce(a.note, ''))) > 0
                   or exists (select t.id from Task t where t.id = a.taskId
                              and locate(lower(:query), lower(coalesce(t.description, ''))) > 0))
            order by a.occurredAt desc, a.id desc
            """)
    List<TaskActivity> search(String query, Long category, LocalDateTime start, LocalDateTime end);

    List<TaskActivity> findByTaskIdOrderByOccurredAtDescIdDesc(Long taskId);

    List<TaskActivity> findAllByOrderByIdAsc();

    Optional<TaskActivity> findFirstByTaskIdOrderByIdDesc(Long taskId);
}
