package com.example.project_flow.controller;

import com.example.project_flow.dto.RecordDtos.AiVerificationRequest;
import com.example.project_flow.dto.RecordDtos.AiVerificationResponse;
import com.example.project_flow.dto.RecordDtos.DailyReportRequest;
import com.example.project_flow.dto.RecordDtos.DailyReportResponse;
import com.example.project_flow.dto.RecordDtos.DeadlineRequest;
import com.example.project_flow.dto.RecordDtos.DeadlineResponse;
import com.example.project_flow.dto.RecordDtos.ErrorEventRequest;
import com.example.project_flow.dto.RecordDtos.ErrorLogRequest;
import com.example.project_flow.dto.RecordDtos.ErrorLogResponse;
import com.example.project_flow.dto.RecordDtos.EvidenceRequest;
import com.example.project_flow.dto.RecordDtos.EvidenceResponse;
import com.example.project_flow.dto.RecordDtos.PhaseRequest;
import com.example.project_flow.dto.RecordDtos.PhaseResponse;
import com.example.project_flow.dto.RecordDtos.RequirementRequest;
import com.example.project_flow.dto.RecordDtos.RequirementResponse;
import com.example.project_flow.dto.RecordDtos.ResponsibilityRequest;
import com.example.project_flow.dto.RecordDtos.ResponsibilityResponse;
import com.example.project_flow.dto.RecordDtos.TeamRoleRequest;
import com.example.project_flow.dto.RecordDtos.TeamRoleResponse;
import com.example.project_flow.service.AiVerificationService;
import com.example.project_flow.service.DailyReportService;
import com.example.project_flow.service.DeadlineService;
import com.example.project_flow.service.ErrorLogService;
import com.example.project_flow.service.EvidenceService;
import com.example.project_flow.service.PhaseService;
import com.example.project_flow.service.RequirementService;
import com.example.project_flow.service.ResponsibilityService;
import com.example.project_flow.service.TeamRoleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

public final class ProjectRecordControllers {

    private ProjectRecordControllers() {
    }

    @RestController
    @RequestMapping("/api/phases")
    public static class Phases extends ProjectRecordController<PhaseRequest, PhaseResponse> {
        public Phases(PhaseService service) {
            super(service);
        }
    }

    @RestController
    @RequestMapping("/api/team-roles")
    public static class TeamRoles extends ProjectRecordController<TeamRoleRequest, TeamRoleResponse> {
        public TeamRoles(TeamRoleService service) {
            super(service);
        }
    }

    @RestController
    @RequestMapping("/api/responsibilities")
    public static class Responsibilities extends ProjectRecordController<ResponsibilityRequest, ResponsibilityResponse> {
        public Responsibilities(ResponsibilityService service) {
            super(service);
        }
    }

    @RestController
    @RequestMapping("/api/deadlines")
    public static class Deadlines extends ProjectRecordController<DeadlineRequest, DeadlineResponse> {
        public Deadlines(DeadlineService service) {
            super(service);
        }
    }

    @RestController
    @RequestMapping("/api/requirements")
    public static class Requirements extends ProjectRecordController<RequirementRequest, RequirementResponse> {
        public Requirements(RequirementService service) {
            super(service);
        }
    }

    @RestController
    @RequestMapping("/api/error-logs")
    public static class ErrorLogs extends ProjectRecordController<ErrorLogRequest, ErrorLogResponse> {
        private final ErrorLogService service;

        public ErrorLogs(ErrorLogService service) {
            super(service);
            this.service = service;
        }

        @PostMapping("/{id}/events")
        public ErrorLogResponse addEvent(@PathVariable Long id, @Valid @RequestBody ErrorEventRequest request) {
            return service.addEvent(id, request.note());
        }
    }

    @RestController
    @RequestMapping("/api/ai-verifications")
    public static class AiVerifications extends ProjectRecordController<AiVerificationRequest, AiVerificationResponse> {
        public AiVerifications(AiVerificationService service) {
            super(service);
        }
    }

    @RestController
    @RequestMapping("/api/daily-reports")
    public static class DailyReports extends ProjectRecordController<DailyReportRequest, DailyReportResponse> {
        public DailyReports(DailyReportService service) {
            super(service);
        }
    }

    @RestController
    @RequestMapping("/api/evidence")
    public static class EvidenceLinks extends ProjectRecordController<EvidenceRequest, EvidenceResponse> {
        public EvidenceLinks(EvidenceService service) {
            super(service);
        }
    }
}
