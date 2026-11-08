package md.dipro.worktime.repository;

import jakarta.persistence.LockModeType;
import md.dipro.worktime.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findAllByOrderByIdAsc();

    // Serialize concurrent start/stop operations for a single employee.
    // This works across threads/instances sharing the same transactional database.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.id = :id")
    Optional<Employee> findLockedById(@Param("id") long id);
}
