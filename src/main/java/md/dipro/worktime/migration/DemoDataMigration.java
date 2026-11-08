package md.dipro.worktime.migration;

import md.dipro.worktime.service.WorktimeService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "worktime.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataMigration implements ApplicationRunner {
    private static final String VERSION = "001-demo-projects";

    private final JdbcTemplate jdbc;
    private final WorktimeService worktime;

    public DemoDataMigration(JdbcTemplate jdbc, WorktimeService worktime) {
        this.jdbc = jdbc;
        this.worktime = worktime;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        jdbc.execute("create table if not exists worktime_data_migrations (version varchar(80) primary key)");
        int applied = jdbc.update("""
                insert into worktime_data_migrations (version)
                select ? where not exists (select 1 from worktime_data_migrations where version = ?)
                """, VERSION, VERSION);
        if (applied == 0) return;

        if (worktime.listEmployees().isEmpty()) {
            worktime.addEmployee("Alex Morgan");
        }
        worktime.addEmployee("Jamie Parker");
        worktime.addEmployee("Taylor Brooks");

        long portal = worktime.addProject("Customer Portal").id();
        worktime.addTask(portal, "Draft the account dashboard", 90);
        worktime.addTask(portal, "Build the profile form", 120);
        worktime.addTask(portal, "Review the mobile layout", 45);

        long reporting = worktime.addProject("Operations Reporting").id();
        worktime.addTask(reporting, "Define report columns", 60);
        worktime.addTask(reporting, "Implement the summary view", 150);
        worktime.addTask(reporting, "Verify sample exports", 30);
    }
}
