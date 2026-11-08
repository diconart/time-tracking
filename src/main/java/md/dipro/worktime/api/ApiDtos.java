package md.dipro.worktime.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

// Jackson's SNAKE_CASE setting preserves the original frontend's API contract.
public final class ApiDtos {
    private ApiDtos() { }

    public record EmployeeCreate(@NotBlank @Size(max = 80) String name) { }
    public record TimerStart(@NotBlank @Size(max = 300) String description) { }
    public record SessionStop(Instant stoppedAt) { }

    public record EmployeeView(long id, String name) { }
    public record EmployeeListRow(long id, String name, String activeTask) { }
    public record ProjectCreate(@NotBlank @Size(max = 80) String name) { }
    public record TaskCreate(@NotBlank @Size(max = 300) String description,
                             @Min(0) @Max(525600) int estimatedMinutes) { }
    public record ProjectView(long id, String name, long taskCount, long estimatedMinutes,
                              double executedSeconds, boolean working, long runningCount) { }
    public record TaskView(long id, String description, int estimatedMinutes,
                           double executedSeconds, boolean working, long runningCount) { }
    public record ProjectDetail(long id, String name, List<TaskView> tasks, long estimatedMinutes,
                                double executedSeconds, boolean working, long runningCount) { }
    public record WorkSession(long id, long userId, String userName, Long projectId,
                              String projectName, Long taskId, String description,
                              Instant startedAt, Instant stoppedAt, double durationSeconds) { }
    public record EntryView(long id, long employeeId, String description,
                            Instant startedAt, Instant stoppedAt) { }
    public record TimerStatus(boolean running, EntryView entry) { }

    public record EntryReport(long id, long employeeId, String description,
                              Instant startedAt, Instant stoppedAt,
                              boolean running, double secondsInPeriod) { }
    public record EmployeeReport(EmployeeView employee, double totalSeconds,
                                 List<EntryReport> entries) { }
    public record Report(Instant start, Instant end, Instant generatedAt,
                         double companyTotalSeconds, List<EmployeeReport> employees) { }
}
