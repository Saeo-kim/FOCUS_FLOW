package com.example.project_flow.service;

import com.example.project_flow.dto.RecordDtos.TeamRoleRequest;
import com.example.project_flow.dto.RecordDtos.TeamRoleResponse;
import com.example.project_flow.entity.TeamRole;
import com.example.project_flow.repository.TeamRoleRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class TeamRoleService extends ProjectRecordService<TeamRole, TeamRoleRequest, TeamRoleResponse> {

    public TeamRoleService(TeamRoleRepository repository, References refs, ActivityLogService activity) {
        super(repository, refs, activity);
    }

    @Override
    protected TeamRole newEntity() {
        return new TeamRole();
    }

    @Override
    protected void apply(TeamRole role, TeamRoleRequest q) {
        role.update(q.roleName().trim(), References.clean(q.duties()), q.mine());
    }

    @Override
    protected TeamRoleResponse toResponse(TeamRole r) {
        return new TeamRoleResponse(r.getId(), r.getProjectId(), r.getRoleName(), r.getDuties(), r.isMine());
    }

    @Override
    protected String noun() {
        return "team role";
    }

    @Override
    protected String titleOf(TeamRole role) {
        return role.getRoleName();
    }

    @Override
    protected Sort sort() {
        return Sort.by("id");
    }
}
