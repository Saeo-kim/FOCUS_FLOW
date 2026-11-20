package com.example.project_flow.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** Records the safe defaults once as an approved policy. Existing records are never changed. */
@Component
@Order(6)
public class SecurityDefaultsSeeder implements ApplicationRunner {

    private final SecurityPolicyRepository policies;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SecurityDefaultsSeeder(SecurityPolicyRepository policies, JdbcTemplate jdbc, Clock clock) {
        this.policies = policies;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Migration: system-recorded policies have no human author, so the column must allow NULL.
        // MODIFY is idempotent and touches no existing row values.
        jdbc.execute("ALTER TABLE security_policies MODIFY authored_by_user_id BIGINT NULL");
        if (!policies.findByPolicyKeyOrderByVersionDesc(SecurityDefaults.POLICY_KEY).isEmpty()) return;
        LocalDateTime now = LocalDateTime.now(clock);
        SecurityPolicy p = new SecurityPolicy(SecurityDefaults.POLICY_KEY, 1, "AI and information access defaults",
                String.join("\n", SecurityDefaults.RULES), null, "FOCUS_FLOW defaults", now);
        p.approve(null, "FOCUS_FLOW defaults (changeable only by the Owner)", now);
        policies.save(p);
    }
}
