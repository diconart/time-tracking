package md.dipro.worktime.repository;

import md.dipro.worktime.domain.TimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {
    @EntityGraph(attributePaths = {"employee", "task", "task.project"})
    List<TimeEntry> findAllByOrderByStartedAtDescIdDesc();

    interface TaskTiming {
        long getTaskId();
        long getElapsedMillis();
        long getRunningCount();
    }

    @Query(value = """
            select task_id as "taskId",
                   sum(datediff('MILLISECOND', started_at,
                       coalesce(stopped_at, greatest(started_at, cast(:snapshot as timestamp with time zone))))) as "elapsedMillis",
                   sum(case when stopped_at is null then 1 else 0 end) as "runningCount"
            from time_entries where task_id is not null group by task_id
            """, nativeQuery = true)
    List<TaskTiming> taskTimings(@Param("snapshot") Instant snapshot);

    Optional<TimeEntry> findFirstByEmployeeIdAndStoppedAtIsNull(Long employeeId);
    List<TimeEntry> findByStoppedAtIsNull();

    @Query("""
            select t from TimeEntry t join fetch t.employee
            where t.startedAt < :periodEnd and (t.stoppedAt is null or t.stoppedAt > :periodStart)
            order by t.startedAt desc, t.id desc
            """)
    List<TimeEntry> overlapping(@Param("periodStart") Instant periodStart,
                                @Param("periodEnd") Instant periodEnd);
}
