package md.dipro.worktime;

import com.jayway.jsonpath.JsonPath;
import md.dipro.worktime.repository.EmployeeRepository;
import md.dipro.worktime.repository.TimeEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:worktime_test;DB_CLOSE_DELAY=-1",
        "worktime.demo-data.enabled=false"})
@AutoConfigureMockMvc
@Import(WorktimeApiTest.DeterministicClockConfiguration.class)
class WorktimeApiTest {
    static final Instant START = Instant.parse("2026-11-08T10:00:00Z");

    @TestConfiguration
    static class DeterministicClockConfiguration {
        @Bean @Primary MutableClock deterministicClock() { return new MutableClock(START); }
    }

    @Autowired MockMvc mvc;
    @Autowired MutableClock clock;
    @Autowired EmployeeRepository employees;
    @Autowired TimeEntryRepository entries;

    @BeforeEach
    void reset() {
        entries.deleteAll();
        employees.deleteAll();
        clock.set(START);
    }

    private long add(String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private MvcResult start(long employeeId, String task) throws Exception {
        return mvc.perform(post("/api/employees/{id}/timer/start", employeeId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"" + task + "\"}"))
                .andReturn();
    }

    private MvcResult report(String start, String end) throws Exception {
        return mvc.perform(get("/api/reports").param("start", start).param("end", end)).andReturn();
    }

    private MvcResult allDay() throws Exception {
        return report("2026-11-08T00:00:00Z", "2026-11-09T00:00:00Z");
    }

    @Test
    void servesFrontendAndPwaFiles() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/manifest.webmanifest")).andExpect(status().isOk());
        mvc.perform(get("/service-worker.js")).andExpect(status().isOk());
        mvc.perform(get("/icons/icon-192.png")).andExpect(status().isOk());
        mvc.perform(get("/api/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void addsEmployeeAndTrimsName() throws Exception {
        long id = add("  Alex Morgan  ");
        mvc.perform(get("/api/employees")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].name").value("Alex Morgan"));
    }

    @Test
    void startsStopsAndReportsExactTime() throws Exception {
        long id = add("Alex Morgan");
        assertThat(start(id, "  Implement dashboard  ").getResponse().getStatus()).isEqualTo(201);
        mvc.perform(get("/api/employees/{id}/timer", id))
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.entry.description").value("Implement dashboard"));
        clock.advance(Duration.ofMinutes(90));
        mvc.perform(post("/api/employees/{id}/timer/stop", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.stopped_at").exists());
        mvc.perform(get("/api/employees/{id}/timer", id))
                .andExpect(jsonPath("$.running").value(false))
                .andExpect(jsonPath("$.entry").value(org.hamcrest.Matchers.nullValue()));
        assertThat(JsonPath.<Number>read(allDay().getResponse().getContentAsString(), "$.company_total_seconds").intValue())
                .isEqualTo(5400);
    }

    @Test
    void canRestartTimerAfterStoppingWithoutLosingHistory() throws Exception {
        long id = add("Alex Morgan");
        assertThat(start(id, "First task").getResponse().getStatus()).isEqualTo(201);
        clock.advance(Duration.ofSeconds(600));
        mvc.perform(post("/api/employees/{id}/timer/stop", id)).andExpect(status().isOk());
        assertThat(start(id, "Second task").getResponse().getStatus()).isEqualTo(201);
        clock.advance(Duration.ofSeconds(300));
        String json = allDay().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(json, "$.employees[0].total_seconds").intValue()).isEqualTo(900);
        assertThat((List<?>) JsonPath.read(json, "$.employees[0].entries")).hasSize(2);
        assertThat((List<?>) JsonPath.read(json, "$.employees[0].entries[?(@.running == true)]")).hasSize(1);
    }

    @Test
    void emptyReportWithNoEmployeesReturnsEmptyArray() throws Exception {
        mvc.perform(get("/api/reports").param("start", "2026-11-08T00:00:00Z")
                        .param("end", "2026-11-09T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employees").isEmpty())
                .andExpect(jsonPath("$.company_total_seconds").value(0));
    }

    @Test
    void employeeWithoutRecordsShowsZeroNotOmitted() throws Exception {
        long id = add("Alex Morgan");
        String json = allDay().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(json, "$.employees[0].employee.id").longValue()).isEqualTo(id);
        assertThat(JsonPath.<Number>read(json, "$.employees[0].total_seconds").intValue()).isZero();
        assertThat((List<?>) JsonPath.read(json, "$.employees[0].entries")).isEmpty();
    }

    @Test
    void reportSeparatesEmployeesAndSumsCompany() throws Exception {
        long alex = add("Alex Morgan");
        long sam = add("Sam Taylor");
        start(alex, "Design");
        start(sam, "QA");
        clock.advance(Duration.ofSeconds(600));
        mvc.perform(post("/api/employees/{id}/timer/stop", alex));
        clock.advance(Duration.ofSeconds(180));
        mvc.perform(post("/api/employees/{id}/timer/stop", sam));
        String json = allDay().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(json, "$.company_total_seconds").intValue()).isEqualTo(1380);
        assertThat(JsonPath.<Number>read(json, "$.employees[0].total_seconds").intValue()).isEqualTo(600);
        assertThat(JsonPath.<Number>read(json, "$.employees[1].total_seconds").intValue()).isEqualTo(780);
    }

    @Test
    void reportClipsSessionAtBothPeriodBoundaries() throws Exception {
        long id = add("Alex Morgan");
        start(id, "Task");
        clock.advance(Duration.ofHours(3));
        mvc.perform(post("/api/employees/{id}/timer/stop", id));
        String json = report("2026-11-08T10:30:00Z", "2026-11-08T12:00:00Z")
                .getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(json, "$.company_total_seconds").intValue()).isEqualTo(5400);
        String outside = report("2026-11-08T13:00:00Z", "2026-11-08T14:00:00Z")
                .getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(outside, "$.company_total_seconds").intValue()).isZero();
        assertThat((List<?>) JsonPath.read(outside, "$.employees[0].entries")).isEmpty();
    }

    @Test
    void runningTimerIsNotCountedIntoFuture() throws Exception {
        long id = add("Alex Morgan");
        start(id, "Task");
        clock.advance(Duration.ofSeconds(60));
        String json = report("2026-11-08T00:00:00Z", "2026-11-10T00:00:00Z")
                .getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(json, "$.company_total_seconds").intValue()).isEqualTo(60);
    }

    @Test
    void doubleStartAndStopWithoutStartAreConflicts() throws Exception {
        long id = add("Alex Morgan");
        mvc.perform(post("/api/employees/{id}/timer/stop", id)).andExpect(status().isConflict());
        assertThat(start(id, "Task").getResponse().getStatus()).isEqualTo(201);
        assertThat(start(id, "Again").getResponse().getStatus()).isEqualTo(409);
        mvc.perform(post("/api/employees/{id}/timer/stop", id)).andExpect(status().isOk());
        mvc.perform(post("/api/employees/{id}/timer/stop", id)).andExpect(status().isConflict());
    }

    @Test
    void simultaneousStartsHaveExactlyOneWinner() throws Exception {
        long id = add("Alex Morgan");
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    gate.await();
                    return start(id, "Parallel").getResponse().getStatus();
                }));
            }
            gate.countDown();
            List<Integer> results = new ArrayList<>();
            for (var f : futures) results.add(f.get());
            assertThat(results).containsExactlyInAnyOrder(201, 409);
        }
    }

    @Test
    void rejectsInvalidInputsAndUnknownEmployees() throws Exception {
        mvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "x".repeat(81) + "\"}"))
                .andExpect(status().isUnprocessableEntity());
        assertThat(start(999999, "Task").getResponse().getStatus()).isEqualTo(404);
        mvc.perform(post("/api/employees/999999/timer/stop")).andExpect(status().isNotFound());
        long id = add("Alex Morgan");
        assertThat(start(id, "   ").getResponse().getStatus()).isEqualTo(422);
        assertThat(report("2026-11-09T00:00:00Z", "2026-11-08T00:00:00Z")
                .getResponse().getStatus()).isEqualTo(422);
        assertThat(report("2026-11-08T00:00:00", "2026-11-09T00:00:00Z")
                .getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void respectsTimezoneOffsets() throws Exception {
        long id = add("Alex Morgan");
        start(id, "Task");
        clock.advance(Duration.ofHours(1));
        mvc.perform(post("/api/employees/{id}/timer/stop", id));
        String json = report("2026-11-08T12:00:00+02:00", "2026-11-08T13:00:00+02:00")
                .getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(json, "$.company_total_seconds").intValue()).isEqualTo(3600);
    }
}
