package com.example.project_flow.admin.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DepartmentColumnMigrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired DepartmentColumnMigration migration;

    private String columnType(String table) {
        return jdbc.queryForObject(
                "SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'department'",
                String.class, table);
    }

    @Test
    void anOlderDepartmentColumnIsWidenedToEveryCurrentDepartment() {
        // Simulate a database created before INFORMATION_SECURITY existed. Existing rows keep their values.
        String older = Arrays.stream(Department.values())
                .filter(d -> d != Department.INFORMATION_SECURITY)
                .map(d -> "'" + d.name() + "'")
                .collect(Collectors.joining(","));
        jdbc.execute("ALTER TABLE app_users MODIFY department ENUM(" + older + ") NULL");
        assertTrue(!columnType("app_users").contains("INFORMATION_SECURITY"));

        migration.migrate();

        for (Department d : Department.values()) {
            assertTrue(columnType("app_users").contains("'" + d.name() + "'"), "missing " + d);
            assertTrue(columnType("org_positions").contains("'" + d.name() + "'"), "missing " + d);
        }
    }

    @Test
    void theMigrationKeepsNullabilityAndCanRunAgain() {
        migration.migrate();
        migration.migrate();
        assertEquals("YES", jdbc.queryForObject(
                "SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_users' AND COLUMN_NAME = 'department'",
                String.class));
        assertEquals("NO", jdbc.queryForObject(
                "SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'org_positions' AND COLUMN_NAME = 'department'",
                String.class));
    }
}
