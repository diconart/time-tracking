package md.dipro.worktime.repository;

import md.dipro.worktime.domain.ProjectTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectTaskRepository extends JpaRepository<ProjectTask, Long> {
    List<ProjectTask> findAllByOrderByIdAsc();
}
