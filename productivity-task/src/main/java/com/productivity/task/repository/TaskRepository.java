package com.productivity.task.repository;

import com.productivity.task.entity.Task;
import com.productivity.task.enums.TaskStatus;
import com.productivity.task.enums.TaskTimerStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByPlannedDateAndArchivedFalseOrderByPriorityDescCreatedAtAsc(LocalDate date);
    List<Task> findByStatusAndArchivedFalseOrderByCompletedAtDesc(TaskStatus status);
    List<Task> findByDeadlineBeforeAndStatusNotAndArchivedFalseOrderByDeadlineAsc(LocalDate date, TaskStatus status);
    List<Task> findByTimerStatus(TaskTimerStatus timerStatus);
    Optional<Task> findFirstByTimerStatus(TaskTimerStatus timerStatus);
    long countByStatusAndArchivedFalse(TaskStatus status);
    long countByArchivedFalse();
    long countByPlannedDateAndArchivedFalse(LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :id")
    Optional<Task> findForTimelineEdit(Long id);

    @Modifying
    @Query("update Task t set t.createdAt = :correctedAt where t.id = :id and t.createdAt = :previousAt")
    int correctCreatedAt(Long id, LocalDateTime previousAt, LocalDateTime correctedAt);

    @Query("select distinct t.plannedDate from Task t where t.archived = false and t.plannedDate between :start and :end")
    List<LocalDate> findActivePlannedDates(@Param("start") LocalDate start, @Param("end") LocalDate end);

    long countByCompletedAtBetween(LocalDateTime start, LocalDateTime end);
    long countByCreatedAtBetweenAndArchivedFalse(LocalDateTime start, LocalDateTime end);

    @Query("select t from Task t where t.archived = false and " +
           "(:query is null or :query = '' or lower(t.name) like lower(concat('%', :query, '%')) or lower(coalesce(t.description,'')) like lower(concat('%', :query, '%'))) and " +
           "(:categoryId is null or t.category.id = :categoryId) and " +
           "(:status is null or t.status = :status) and " +
           "(:priority is null or t.priority = :priority) " +
           "order by t.createdAt desc")
    List<Task> search(@Param("query") String query,
                     @Param("categoryId") Long categoryId,
                     @Param("status") TaskStatus status,
                     @Param("priority") com.productivity.task.enums.Priority priority);

    default List<Task> search(String query, Long categoryId) {
        return search(query, categoryId, null, null);
    }

    @Query("select t.category.name, count(t) from Task t where t.archived = false group by t.category.name order by count(t) desc")
    List<Object[]> countByCategory();

    @Query("select t.category.name, count(t), sum(case when t.status = 'COMPLETED' then 1 else 0 end) " +
           "from Task t where t.archived = false group by t.category.name order by count(t) desc")
    List<Object[]> completionByCategory();

    @Query("select t from Task t where t.status = 'COMPLETED' and t.archived = false and " +
           "lower(t.name) like lower(concat('%', :query, '%')) and " +
           "(:categoryId is null or t.category.id = :categoryId) and " +
           "t.completedAt >= :completedFrom and t.completedAt < :completedBefore order by t.completedAt desc")
    List<Task> searchCompleted(@Param("query") String query, @Param("categoryId") Long categoryId,
                               @Param("completedFrom") LocalDateTime completedFrom,
                               @Param("completedBefore") LocalDateTime completedBefore);

    @Query("select t from Task t where t.archived = false and (" +
           "t.plannedDate = :today or " +
           "(t.plannedDate < :today and t.status <> :completedStatus) or " +
           "(t.plannedDate < :today and t.status = :completedStatus and t.completedAt >= :todayStart and t.completedAt < :todayEnd)" +
           ") order by t.priority desc, t.plannedDate asc, t.createdAt asc")
    List<Task> findForBoardWithRollover(@Param("today") LocalDate today,
                                       @Param("completedStatus") TaskStatus completedStatus,
                                       @Param("todayStart") LocalDateTime todayStart,
                                       @Param("todayEnd") LocalDateTime todayEnd);
}
