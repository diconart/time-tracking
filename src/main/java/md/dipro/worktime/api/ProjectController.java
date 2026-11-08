package md.dipro.worktime.api;

import jakarta.validation.Valid;
import md.dipro.worktime.service.WorktimeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ProjectController {
    private final WorktimeService service;

    public ProjectController(WorktimeService service) { this.service = service; }

    @GetMapping("/projects")
    public List<ApiDtos.ProjectView> projects() { return service.listProjects(); }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.ProjectView addProject(@Valid @RequestBody ApiDtos.ProjectCreate input) {
        return service.addProject(input.name());
    }

    @GetMapping("/projects/{id}")
    public ApiDtos.ProjectDetail project(@PathVariable long id) { return service.project(id); }

    @PostMapping("/projects/{id}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.TaskView addTask(@PathVariable long id, @Valid @RequestBody ApiDtos.TaskCreate input) {
        return service.addTask(id, input.description(), input.estimatedMinutes());
    }

    @PostMapping("/tasks/{id}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.WorkSession start(@PathVariable long id) { return service.startTask(id); }

    @GetMapping("/sessions/active")
    public Optional<ApiDtos.WorkSession> active() { return Optional.ofNullable(service.activeSession()); }

    @PostMapping("/sessions/{id}/stop")
    public ApiDtos.WorkSession stop(@PathVariable long id, @RequestBody(required = false) ApiDtos.SessionStop input) {
        return service.stopSession(id, input == null ? null : input.stoppedAt());
    }

    @GetMapping("/sessions")
    public List<ApiDtos.WorkSession> sessions(@RequestParam(required = false) Long userId) {
        return service.sessions(userId);
    }

    @PostMapping("/sessions/export")
    public ResponseEntity<byte[]> export(@RequestBody List<Long> ids) throws IOException {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=work-sessions.xlsx")
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(SessionWorkbook.write(service.sessions(null), ids));
    }
}
