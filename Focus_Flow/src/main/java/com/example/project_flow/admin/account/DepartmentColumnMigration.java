package com.example.project_flow.admin.account;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Keeps the department columns in step with the {@link Department} list.
 *
 * Hibernate's ddl-auto=update creates a MySQL ENUM once and never widens it. A database created
 * before a department was added would then refuse that department with a server error. MODIFY
 * only adds the missing values, keeps every existing row, and is safe to run on each start.
 */
@Component
public class DepartmentColumnMigration implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public DepartmentColumnMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        migrate();
    }

    /** Public so tests can run the same step again after changing the column. */
    @Transactional
    public void migrate() {
        String values = Arrays.stream(Department.values())
                .map(d -> "'" + d.name() + "'")
                .collect(Collectors.joining(","));
        // Nullability matches the entities: a member may have no department, a position must have one.
        jdbc.execute("ALTER TABLE app_users MODIFY department ENUM(" + values + ") NULL");
        jdbc.execute("ALTER TABLE org_positions MODIFY department ENUM(" + values + ") NOT NULL");
    }
}
