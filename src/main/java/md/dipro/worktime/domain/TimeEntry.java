package md.dipro.worktime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "time_entries", indexes = {
        @Index(name = "idx_entry_employee_start", columnList = "employee_id,started_at")
})
public class TimeEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, foreignKey = @ForeignKey(name = "fk_entry_employee"))
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private ProjectTask task;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    protected TimeEntry() { }

    public TimeEntry(Employee employee, String description, Instant startedAt) {
        this.employee = employee;
        this.description = description;
        this.startedAt = startedAt;
    }

    public TimeEntry(Employee employee, ProjectTask task, Instant startedAt) {
        this(employee, task.getDescription(), startedAt);
        this.task = task;
    }

    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public ProjectTask getTask() { return task; }
    public String getDescription() { return description; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getStoppedAt() { return stoppedAt; }

    public void stop(Instant at) {
        if (stoppedAt != null) {
            throw new IllegalStateException("Timer already stopped");
        }
        stoppedAt = at.isBefore(startedAt) ? startedAt : at;
    }
}
