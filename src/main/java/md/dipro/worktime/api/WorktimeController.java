package md.dipro.worktime.api;

import jakarta.validation.Valid;
import md.dipro.worktime.service.WorktimeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class WorktimeController {
    private final WorktimeService service;

    public WorktimeController(WorktimeService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/employees")
    public List<ApiDtos.EmployeeListRow> employees() {
        return service.listEmployeesWithActivity();
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.EmployeeView addEmployee(@Valid @RequestBody ApiDtos.EmployeeCreate payload) {
        return service.addEmployee(payload.name());
    }

    @PutMapping("/employees/{id}")
    public ApiDtos.EmployeeView renameEmployee(@PathVariable long id,
                                                @Valid @RequestBody ApiDtos.EmployeeCreate payload) {
        return service.renameEmployee(id, payload.name());
    }

    @GetMapping("/employees/{id}/timer")
    public ApiDtos.TimerStatus timer(@PathVariable long id) {
        return service.activeTimer(id);
    }

    @PostMapping("/employees/{id}/timer/start")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.EntryView start(@PathVariable long id, @Valid @RequestBody ApiDtos.TimerStart payload) {
        return service.startTimer(id, payload.description());
    }

    @PostMapping("/employees/{id}/timer/stop")
    public ApiDtos.EntryView stop(@PathVariable long id) {
        return service.stopTimer(id);
    }

    @GetMapping("/reports")
    public ApiDtos.Report report(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end) {
        return service.report(start.toInstant(), end.toInstant());
    }
}
