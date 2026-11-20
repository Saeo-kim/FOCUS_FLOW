package com.example.project_flow.admin.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Keeps the department columns in step with the {@link Department} list.
 *
 * Hibernate's ddl-auto=update creates a MySQL ENUM once and never widens it. A database created
 * before a department was added would then refuse that department with a server error. The column
 * is changed only when it is missing a value, so normal starts do not touch the table. Existing rows
 * keep their values. If the change cannot run (for example, missing privileges), the application
 * still starts and logs a warning, so the rest of the workspace stays usable.
 */
@Component
public class DepartmentColumnMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DepartmentColumnMigration.class);

    private final JdbcTemplate jdbc;

    public DepartmentColumnMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            migrate();
        } catch (DataAccessException e) {
            log.warn("Could not update the department list in the database. Departments added after the last update may be refused until this is fixed: {}",
                    e.getMostSpecificCause().getMessage());
        }
    }

    /** Public so tests can run the same step again after changing the column. */
    public void migrate() {
        String values = Arrays.stream(Department.values())
                .map(d -> "'" + d.name() + "'")
                .collect(Collectors.joining(","));
        // Nullability matches the entities: a member may have no department, a position must have one.
        widen("app_users", values, "NULL");
        widen("org_positions", values, "NOT NULL");
    }

    private void widen(String table, String values, String nullability) {
        List<String> types = jdbc.queryForList(
                "SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'department'",
                String.class, table);
        if (types.isEmpty()) {
            return; // the table does not exist yet; Hibernate creates it with every department
        }
        boolean complete = Arrays.stream(Department.values())
                .allMatch(d -> types.get(0).contains("'" + d.name() + "'"));
        if (!complete) {
            jdbc.execute("ALTER TABLE " + table + " MODIFY department ENUM(" + values + ") " + nullability);
        }
    }
}
