package com.example.project_flow.aiteam;

import java.util.Map;

/**
 * The report types the CFO can produce. Keys must match the other application's kinds.
 * A request without a kind lets the other application choose from the title and instructions.
 */
public final class AiRequestKinds {

    public static final Map<String, String> CFO = Map.of(
            "monthly_report", "월간 재무 보고서",
            "financial_analysis", "사업 손익 분석",
            "cash_flow", "현금흐름·자금 소진 점검",
            "bookkeeping", "장부 정리 현황",
            "tax_summary", "세무 준비 자료",
            "portfolio", "투자 자산 현황",
            "planning", "재무 계획·권고",
            "risk_audit", "재무 위험 점검");

    private AiRequestKinds() {
    }

    public static boolean isValidFor(String agentKey, String kind) {
        return kind == null || ("cfo".equals(agentKey) && CFO.containsKey(kind));
    }
}
