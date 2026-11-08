package md.dipro.worktime;

import md.dipro.worktime.service.WorktimeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceRestartTest {
    @TempDir Path tempDir;

    // Real application context close/reopen with exactly the same on-disk database.
    private ConfigurableApplicationContext startApplication(String jdbcUrl) {
        return startApplication(jdbcUrl, false);
    }

    private ConfigurableApplicationContext startApplication(String jdbcUrl, boolean seedDemoData) {
        return new SpringApplicationBuilder(WorktimeTrackerApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.datasource.url=" + jdbcUrl,
                     "--spring.main.banner-mode=off",
                     "--worktime.demo-data.enabled=" + seedDemoData,
                     "--spring.jpa.hibernate.ddl-auto=update");
    }

    @Test
    void demoMigrationAddsDataOnceAndKeepsExistingRecords() {
        String jdbcUrl = "jdbc:h2:file:" + tempDir.resolve("demo").toAbsolutePath()
                + ";DB_CLOSE_ON_EXIT=FALSE";
        try (var existing = startApplication(jdbcUrl)) {
            var service = existing.getBean(WorktimeService.class);
            service.addEmployee("Existing demo user");
        }
        for (int startup = 0; startup < 2; startup++) {
            try (var migrated = startApplication(jdbcUrl, true)) {
                var service = migrated.getBean(WorktimeService.class);
                assertThat(service.listEmployees()).hasSize(3);
                assertThat(service.listEmployees().getFirst().name()).isEqualTo("Existing demo user");
                assertThat(service.listProjects()).hasSize(2).allSatisfy(project -> {
                    assertThat(project.taskCount()).isEqualTo(3);
                    assertThat(project.estimatedMinutes()).isPositive();
                    assertThat(project.executedSeconds()).isZero();
                    assertThat(project.working()).isFalse();
                });
            }
        }
    }

    @Test
    void activeTimerSurvivesServerRestartAndStopIsDurable() {
        String jdbcUrl = "jdbc:h2:file:" + tempDir.resolve("timerdb").toAbsolutePath()
                + ";DB_CLOSE_ON_EXIT=FALSE";
        long employeeId;
        long entryId;
        try (ConfigurableApplicationContext first = startApplication(jdbcUrl)) {
            WorktimeService service = first.getBean(WorktimeService.class);
            employeeId = service.addEmployee("Alex Morgan").id();
            entryId = service.startTimer(employeeId, "Plan sprint").id();
        }
        try (ConfigurableApplicationContext second = startApplication(jdbcUrl)) {
            WorktimeService service = second.getBean(WorktimeService.class);
            assertThat(service.listEmployees()).hasSize(1);
            assertThat(service.activeTimer(employeeId).running()).isTrue();
            assertThat(service.activeTimer(employeeId).entry().id()).isEqualTo(entryId);
            assertThat(service.stopTimer(employeeId).stoppedAt()).isNotNull();
        }
        try (ConfigurableApplicationContext third = startApplication(jdbcUrl)) {
            WorktimeService service = third.getBean(WorktimeService.class);
            assertThat(service.activeTimer(employeeId).running()).isFalse();
            assertThat(service.report(java.time.Instant.parse("2020-01-01T00:00:00Z"),
                    java.time.Instant.parse("2030-01-01T00:00:00Z"))
                    .employees().getFirst().entries()).hasSize(1);
        }
    }
}
