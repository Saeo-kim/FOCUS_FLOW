package com.example.project_flow.security;

import com.example.project_flow.legal.OrgPosition;
import com.example.project_flow.legal.OrgPositionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** Registers the executive security positions once, vacant. Existing rows are never changed. */
@Component
@Order(4)
public class SecurityPositionSeeder implements ApplicationRunner {

    private final SecurityPositionCatalog catalog;
    private final OrgPositionRepository positions;
    private final Clock clock;

    public SecurityPositionSeeder(SecurityPositionCatalog catalog, OrgPositionRepository positions, Clock clock) {
        this.catalog = catalog;
        this.positions = positions;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (SecurityPositionCatalog.Position p : catalog.all()) {
            if (positions.findByPositionKey(p.key()).isEmpty()) {
                positions.save(new OrgPosition(p.key(), p.title(), null, SecurityPositionCatalog.DEPARTMENT, p.mission(),
                        LocalDateTime.now(clock)));
            }
        }
    }
}
