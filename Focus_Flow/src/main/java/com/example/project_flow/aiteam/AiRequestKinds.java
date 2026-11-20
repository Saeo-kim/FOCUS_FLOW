package com.example.project_flow.aiteam;

import java.util.Map;

/**
 * The report types the CFO can produce. Keys must match the other application's kinds.
 * A request without a kind lets the other application choose from the title and instructions.
 */
public final class AiRequestKinds {

    public static final Map<String, String> CFO = Map.of(
            "monthly_report", "Monthly financial report",
            "financial_analysis", "Business profit and loss analysis",
            "cash_flow", "Cash flow and runway check",
            "bookkeeping", "Bookkeeping status",
            "tax_summary", "Tax preparation material",
            "portfolio", "Investment account summary",
            "planning", "Financial planning and recommendations",
            "risk_audit", "Financial risk review");

    private AiRequestKinds() {
    }

    public static boolean isValidFor(String agentKey, String kind) {
        return kind == null || ("cfo".equals(agentKey) && CFO.containsKey(kind));
    }
}
