package com.example.project_flow.legal;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Registers the legal and compliance position and its five functions, once. Existing rows are
 * never changed or duplicated, and the position is created vacant: no person or AI agent is attached.
 */
@Component
@Order(3)
public class LegalPositionSeeder implements ApplicationRunner {

    private final LegalCatalog catalog;
    private final OrgPositionRepository positions;
    private final OrgPositionFunctionRepository functions;
    private final Clock clock;

    public LegalPositionSeeder(LegalCatalog catalog, OrgPositionRepository positions,
                               OrgPositionFunctionRepository functions, Clock clock) {
        this.catalog = catalog;
        this.positions = positions;
        this.functions = functions;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (LegalCatalog.Position p : catalog.all()) {
            if (positions.findByPositionKey(p.key()).isEmpty()) {
                positions.save(new OrgPosition(p.key(), p.title(), p.koreanTitle(), p.department(), p.mission(),
                        LocalDateTime.now(clock)));
            }
            List<OrgPositionFunction> existing = functions.findByPositionKeyOrderBySortOrderAsc(p.key());
            for (int i = 0; i < p.functions().size(); i++) {
                LegalCatalog.Function f = p.functions().get(i);
                boolean present = existing.stream().anyMatch(e -> e.getFunctionKey().equals(f.key()));
                if (!present) {
                    functions.save(new OrgPositionFunction(p.key(), f.key(), f.title(), f.description(), i + 1));
                }
            }
        }
    }
}
