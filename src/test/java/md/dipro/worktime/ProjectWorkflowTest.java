package md.dipro.worktime;

import com.jayway.jsonpath.JsonPath;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:project_workflow;DB_CLOSE_DELAY=-1",
        "worktime.demo-data.enabled=false"})
@AutoConfigureMockMvc
@Import(WorktimeApiTest.DeterministicClockConfiguration.class)
class ProjectWorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired MutableClock clock;

    @Test
    void tracksProjectTaskForUserOneAndExportsSelectedSessionsInOrder() throws Exception {
        long projectId = id(mvc.perform(post("/api/projects").contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Demo project"}
                                """))
                .andExpect(status().isCreated()).andReturn());
        long taskId = id(mvc.perform(post("/api/projects/{id}/tasks", projectId).contentType(APPLICATION_JSON)
                        .content("""
                                {"description":"Review report","estimated_minutes":75}
                                """))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(get("/api/projects")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].task_count").value(1))
                .andExpect(jsonPath("$[0].estimated_minutes").value(75))
                .andExpect(jsonPath("$[0].executed_seconds").value(0.0))
                .andExpect(jsonPath("$[0].working").value(false));
        mvc.perform(get("/api/projects/{id}", projectId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].id").value(taskId));
        mvc.perform(post("/api/tasks/{id}/sessions", taskId)).andExpect(status().isConflict());
        mvc.perform(post("/api/employees").contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Alex Example"}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(1));
        mvc.perform(put("/api/employees/1").contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Alex Demo"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Alex Demo"));
        long first = id(mvc.perform(post("/api/tasks/{id}/sessions", taskId)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.user_id").value(1))
                .andExpect(jsonPath("$.project_id").value(projectId))
                .andExpect(jsonPath("$.task_id").value(taskId)).andReturn());
        mvc.perform(get("/api/sessions/active")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first));
        mvc.perform(get("/api/employees")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active_task").value("Review report"));
        mvc.perform(post("/api/tasks/{id}/sessions", taskId)).andExpect(status().isConflict());

        var stoppedOfflineAt = clock.instant().plusSeconds(40);
        clock.advance(Duration.ofMinutes(2));
        mvc.perform(get("/api/projects/{id}", projectId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.working").value(true))
                .andExpect(jsonPath("$.executed_seconds").value(120.0))
                .andExpect(jsonPath("$.tasks[0].executed_seconds").value(120.0));
        mvc.perform(post("/api/sessions/{id}/stop", first).contentType(APPLICATION_JSON)
                        .content("""
                                {"stopped_at":"%s"}
                                """.formatted(stoppedOfflineAt)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duration_seconds").value(40.0));
        mvc.perform(get("/api/employees")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active_task").doesNotExist());
        mvc.perform(get("/api/projects")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].working").value(false))
                .andExpect(jsonPath("$[0].executed_seconds").value(40.0));
        long second = id(mvc.perform(post("/api/tasks/{id}/sessions", taskId)).andExpect(status().isCreated())
                .andReturn());
        assertThat(second).isNotEqualTo(first);
        mvc.perform(post("/api/sessions/{id}/stop", first)).andExpect(status().isOk())
                .andExpect(jsonPath("$.stopped_at").value(stoppedOfflineAt.toString()));
        mvc.perform(get("/api/sessions/active")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(second));
        mvc.perform(get("/api/sessions").param("userId", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/sessions").param("userId", "2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        long otherUser = id(mvc.perform(post("/api/employees").contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Jamie Demo"}
                                """)).andExpect(status().isCreated()).andReturn());
        String otherUserId = Long.toString(otherUser);
        long otherSession = id(mvc.perform(post("/api/tasks/{id}/sessions", taskId).param("userId", otherUserId))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.user_id").value(otherUser)).andReturn());
        mvc.perform(get("/api/sessions/active").param("userId", otherUserId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(otherSession));
        mvc.perform(get("/api/projects/{id}", projectId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.running_count").value(2))
                .andExpect(jsonPath("$.tasks[0].running_count").value(2));
        mvc.perform(post("/api/sessions/{id}/stop", otherSession)).andExpect(status().isNotFound());
        mvc.perform(post("/api/sessions/{id}/stop", otherSession).param("userId", otherUserId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user_id").value(otherUser));
        mvc.perform(get("/api/sessions/active")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(second));

        byte[] file = mvc.perform(post("/api/sessions/export").contentType(APPLICATION_JSON)
                        .content("[%d,%d]".formatted(first, second)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            var sheet = workbook.getSheet("Work sessions");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo(Long.toString(first));
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Alex Demo");
            assertThat(sheet.getRow(1).getCell(7).getNumericCellValue()).isEqualTo(40.0 / 86400);
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo(Long.toString(second));
        }
    }

    private static long id(MvcResult response) throws Exception {
        return ((Number) JsonPath.read(response.getResponse().getContentAsString(), "$.id")).longValue();
    }
}
