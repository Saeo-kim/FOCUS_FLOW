package com.example.project_flow.aiteam;

import com.example.project_flow.admin.account.Department;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * The AI team, declared in one place. Each entry gets a seat in the member list when the
 * application starts (see {@link AiTeamSeeder}). To add a teammate later, append an entry here;
 * the seat, the assignment flow and the AI team page then pick it up without other changes.
 *
 * Planned, not yet declared: CTO (ENGINEERING), COO (OPERATIONS), CMO (MARKETING_SALES),
 * CPO (DESIGN_PRODUCT), HR Manager (PEOPLE_HR).
 */
@Component
public class AiTeamCatalog {

    static final String OWNERS_OFFICE = "owners-office";

    private static final List<AiTeamMember> MEMBERS = List.of(
            new AiTeamMember(
                    "cfo",
                    OWNERS_OFFICE,
                    "Owner's Office",
                    "CFO (Chief Financial Officer)",
                    Department.FINANCE_ACCOUNTING,
                    "CFO",
                    "#0f766e",
                    List.of(
                            "Accounting and bookkeeping",
                            "Income and expense management",
                            "Business financial analysis",
                            "Cash flow management",
                            "Tax preparation and financial records",
                            "Stock and investment portfolio tracking",
                            "Monthly financial reports",
                            "Financial planning and recommendations"),
                    OWNERS_OFFICE));

    public List<AiTeamMember> all() {
        return MEMBERS;
    }

    public Optional<AiTeamMember> byKey(String key) {
        return MEMBERS.stream().filter(m -> m.key().equals(key)).findFirst();
    }

    public Optional<AiTeamMember> byUsername(String username) {
        return MEMBERS.stream().filter(m -> m.username().equalsIgnoreCase(username)).findFirst();
    }
}
