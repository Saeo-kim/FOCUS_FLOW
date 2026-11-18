package com.example.project_flow.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Makes the audit records immutable in the database itself, not just in the application code:
 * ai activity log rows can never be updated or deleted, and audit finding evidence can never change.
 * Triggers are recreated at startup (DROP then CREATE), which keeps existing rows untouched.
 */
@Component
@Order(5)
public class AuditLogGuard implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public AuditLogGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("DROP TRIGGER IF EXISTS security_ai_log_no_update");
        jdbc.execute("DROP TRIGGER IF EXISTS security_ai_log_no_delete");
        jdbc.execute("DROP TRIGGER IF EXISTS security_finding_evidence_guard");
        jdbc.execute("DROP TRIGGER IF EXISTS security_finding_no_delete");
        jdbc.execute("CREATE TRIGGER security_ai_log_no_update BEFORE UPDATE ON security_ai_activity_log FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AI activity log is append-only'");
        jdbc.execute("CREATE TRIGGER security_ai_log_no_delete BEFORE DELETE ON security_ai_activity_log FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AI activity log is append-only'");
        jdbc.execute("CREATE TRIGGER security_finding_evidence_guard BEFORE UPDATE ON security_audit_findings FOR EACH ROW "
                + "BEGIN IF NEW.evidence <> OLD.evidence OR NEW.evidence_hash <> OLD.evidence_hash "
                + "THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Audit evidence is immutable'; END IF; END");
        jdbc.execute("CREATE TRIGGER security_finding_no_delete BEFORE DELETE ON security_audit_findings FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Audit findings cannot be deleted'");
    }
}
