package md.dipro.worktime.service;

import md.dipro.worktime.api.ApiDtos;
import md.dipro.worktime.domain.Employee;
import md.dipro.worktime.domain.Project;
import md.dipro.worktime.domain.ProjectTask;
import md.dipro.worktime.domain.TimeEntry;
import md.dipro.worktime.repository.EmployeeRepository;
import md.dipro.worktime.repository.ProjectRepository;
import md.dipro.worktime.repository.ProjectTaskRepository;
import md.dipro.worktime.repository.TimeEntryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorktimeService {
    private static final long TRACKING_USER_ID = 1;
    private final EmployeeRepository employees;
    private final TimeEntryRepository entries;
    private final ProjectRepository projects;
    private final ProjectTaskRepository tasks;
    private final Clock clock;

    public WorktimeService(EmployeeRepository employees, TimeEntryRepository entries,
                           ProjectRepository projects, ProjectTaskRepository tasks, Clock clock) {
        this.employees = employees;
        this.entries = entries;
        this.projects = projects;
        this.tasks = tasks;
        this.clock = clock;
    }

    @Transactional
    public ApiDtos.EmployeeView addEmployee(String name) {
        return view(employees.save(new Employee(cleanName(name))));
    }

    @Transactional
    public ApiDtos.EmployeeView renameEmployee(long id, String name) {
        Employee employee = lockEmployee(id);
        employee.rename(cleanName(name));
        return view(employee);
    }

    private static String cleanName(String name) {
        String cleanName = name.trim();
        if (cleanName.isEmpty() || cleanName.length() > 80) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Name must be 1–80 characters");
        }
        return cleanName;
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.ProjectView> listProjects() {
        var tasksByProject = projectTasks();
        return projects.findAllByOrderByIdAsc().stream().map(project -> {
            var detail = detail(project, tasksByProject.getOrDefault(project.getId(), List.of()));
            return new ApiDtos.ProjectView(detail.id(), detail.name(), detail.tasks().size(),
                    detail.estimatedMinutes(), detail.executedSeconds(), detail.working());
        }).toList();
    }

    @Transactional
    public ApiDtos.ProjectView addProject(String name) {
        Project project = projects.save(new Project(cleanName(name)));
        return new ApiDtos.ProjectView(project.getId(), project.getName(), 0, 0, 0, false);
    }

    @Transactional(readOnly = true)
    public ApiDtos.ProjectDetail project(long id) {
        Project project = requireProject(id);
        return detail(project, projectTasks().getOrDefault(id, List.of()));
    }

    @Transactional
    public ApiDtos.TaskView addTask(long projectId, String description, int estimatedMinutes) {
        var task = tasks.save(new ProjectTask(requireProject(projectId), cleanDescription(description), estimatedMinutes));
        return new ApiDtos.TaskView(task.getId(), task.getDescription(), estimatedMinutes, 0, false);
    }

    private Map<Long, List<ApiDtos.TaskView>> projectTasks() {
        var timings = new HashMap<Long, TimeEntryRepository.TaskTiming>();
        entries.taskTimings(now()).forEach(timing -> timings.put(timing.getTaskId(), timing));
        var byProject = new HashMap<Long, List<ApiDtos.TaskView>>();
        for (ProjectTask task : tasks.findAllByOrderByIdAsc()) {
            var timing = timings.get(task.getId());
            var view = new ApiDtos.TaskView(task.getId(), task.getDescription(), task.getEstimatedMinutes(),
                    timing == null ? 0 : timing.getElapsedMillis() / 1000.0,
                    timing != null && timing.getRunningCount() > 0);
            byProject.computeIfAbsent(task.getProject().getId(), id -> new ArrayList<>()).add(view);
        }
        return byProject;
    }

    private static ApiDtos.ProjectDetail detail(Project project, List<ApiDtos.TaskView> tasks) {
        return new ApiDtos.ProjectDetail(project.getId(), project.getName(), tasks,
                tasks.stream().mapToLong(ApiDtos.TaskView::estimatedMinutes).sum(),
                tasks.stream().mapToDouble(ApiDtos.TaskView::executedSeconds).sum(),
                tasks.stream().anyMatch(ApiDtos.TaskView::working));
    }

    @Transactional
    public ApiDtos.WorkSession startTask(long taskId) {
        Employee user = employees.findLockedById(TRACKING_USER_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Create user #1 in Users before starting a task"));
        ProjectTask task = tasks.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        ensureNoActiveTimer(TRACKING_USER_ID);
        Instant start = now();
        return session(entries.saveAndFlush(new TimeEntry(user, task, start)), start);
    }

    @Transactional(readOnly = true)
    public ApiDtos.WorkSession activeSession() {
        return entries.findFirstByEmployeeIdAndStoppedAtIsNull(TRACKING_USER_ID)
                .map(entry -> session(entry, now())).orElse(null);
    }

    @Transactional
    public ApiDtos.WorkSession stopSession(long sessionId, Instant requestedStop) {
        lockEmployee(TRACKING_USER_ID);
        TimeEntry entry = entries.findById(sessionId)
                .filter(candidate -> candidate.getEmployee().getId() == TRACKING_USER_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work session not found"));
        Instant stop = now();
        if (entry.getStoppedAt() == null) {
            entry.stop(requestedStop == null ? stop : min(stop, requestedStop));
            entries.flush();
        }
        return session(entry, stop);
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.WorkSession> sessions(Long userId) {
        Instant snapshot = now();
        return entries.findAllByOrderByStartedAtDescIdDesc().stream()
                .filter(entry -> userId == null || entry.getEmployee().getId().equals(userId))
                .map(entry -> session(entry, snapshot)).toList();
    }

    private Project requireProject(long id) {
        return projects.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private static ApiDtos.WorkSession session(TimeEntry entry, Instant snapshot) {
        ProjectTask task = entry.getTask();
        Project project = task == null ? null : task.getProject();
        Instant end = entry.getStoppedAt() == null ? snapshot : entry.getStoppedAt();
        double seconds = Math.max(0, Duration.between(entry.getStartedAt(), end).toMillis()) / 1000.0;
        return new ApiDtos.WorkSession(entry.getId(), entry.getEmployee().getId(), entry.getEmployee().getName(),
                project == null ? null : project.getId(), project == null ? null : project.getName(),
                task == null ? null : task.getId(), entry.getDescription(), entry.getStartedAt(),
                entry.getStoppedAt(), seconds);
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.EmployeeView> listEmployees() {
        return employees.findAllByOrderByIdAsc().stream().map(WorktimeService::view).toList();
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.EmployeeListRow> listEmployeesWithActivity() {
        var activeTasks = new HashMap<Long, String>();
        for (TimeEntry entry : entries.findByStoppedAtIsNull()) {
            activeTasks.put(entry.getEmployee().getId(), entry.getDescription());
        }
        return employees.findAllByOrderByIdAsc().stream()
                .map(user -> new ApiDtos.EmployeeListRow(user.getId(), user.getName(), activeTasks.get(user.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ApiDtos.TimerStatus activeTimer(long employeeId) {
        requireEmployee(employeeId);
        return entries.findFirstByEmployeeIdAndStoppedAtIsNull(employeeId)
                .map(entry -> new ApiDtos.TimerStatus(true, view(entry)))
                .orElseGet(() -> new ApiDtos.TimerStatus(false, null));
    }

    @Transactional
    public ApiDtos.EntryView startTimer(long employeeId, String description) {
        Employee employee = lockEmployee(employeeId);
        String cleanTask = cleanDescription(description);
        ensureNoActiveTimer(employeeId);
        TimeEntry entry = new TimeEntry(employee, cleanTask, now());
        return view(entries.saveAndFlush(entry));
    }

    private static String cleanDescription(String description) {
        String cleanTask = description.trim();
        if (cleanTask.isEmpty() || cleanTask.length() > 300) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Description must be 1–300 characters");
        }
        return cleanTask;
    }

    private void ensureNoActiveTimer(long employeeId) {
        if (entries.findFirstByEmployeeIdAndStoppedAtIsNull(employeeId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee already has a running timer");
        }
    }

    @Transactional
    public ApiDtos.EntryView stopTimer(long employeeId) {
        lockEmployee(employeeId);
        TimeEntry entry = entries.findFirstByEmployeeIdAndStoppedAtIsNull(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Employee has no running timer"));
        entry.stop(now());
        return view(entries.saveAndFlush(entry));
    }

    @Transactional(readOnly = true)
    public ApiDtos.Report report(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Report end must be after start");
        }
        // A single snapshot: all running timers stop counting at the same instant.
        Instant snapshot = now();
        List<Employee> allEmployees = employees.findAllByOrderByIdAsc();
        Map<Long, List<ApiDtos.EntryReport>> byEmployee = new LinkedHashMap<>();
        for (Employee employee : allEmployees) byEmployee.put(employee.getId(), new ArrayList<>());

        for (TimeEntry entry : entries.overlapping(start, end)) {
            Instant effectiveEnd = entry.getStoppedAt() == null ? snapshot : entry.getStoppedAt();
            Instant overlappingStart = max(start, entry.getStartedAt());
            Instant overlappingEnd = min(end, effectiveEnd);
            if (!overlappingEnd.isAfter(overlappingStart)) continue;

            double seconds = Duration.between(overlappingStart, overlappingEnd).toMillis() / 1000.0;
            byEmployee.get(entry.getEmployee().getId()).add(new ApiDtos.EntryReport(
                    entry.getId(), entry.getEmployee().getId(), entry.getDescription(),
                    entry.getStartedAt(), entry.getStoppedAt(), entry.getStoppedAt() == null, seconds));
        }

        List<ApiDtos.EmployeeReport> rows = new ArrayList<>();
        double companyTotal = 0;
        for (Employee employee : allEmployees) {
            List<ApiDtos.EntryReport> employeeEntries = byEmployee.get(employee.getId());
            double total = employeeEntries.stream().mapToDouble(ApiDtos.EntryReport::secondsInPeriod).sum();
            rows.add(new ApiDtos.EmployeeReport(view(employee), total, employeeEntries));
            companyTotal += total;
        }
        return new ApiDtos.Report(start, end, snapshot, companyTotal, rows);
    }

    private Employee requireEmployee(long id) {
        return employees.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
    }

    private Employee lockEmployee(long id) {
        return employees.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MILLIS); }
    private static Instant min(Instant a, Instant b) { return a.isBefore(b) ? a : b; }
    private static Instant max(Instant a, Instant b) { return a.isAfter(b) ? a : b; }
    private static ApiDtos.EmployeeView view(Employee e) { return new ApiDtos.EmployeeView(e.getId(), e.getName()); }
    private static ApiDtos.EntryView view(TimeEntry e) {
        return new ApiDtos.EntryView(e.getId(), e.getEmployee().getId(), e.getDescription(),
                e.getStartedAt(), e.getStoppedAt());
    }
}
