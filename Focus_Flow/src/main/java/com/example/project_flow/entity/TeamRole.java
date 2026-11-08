package com.example.project_flow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "team_roles")
public class TeamRole extends ProjectRecord {

    @Column(nullable = false, length = 80)
    private String roleName;

    @Column(length = 1000)
    private String duties;

    @Column(name = "is_mine", nullable = false)
    private boolean mine;

    public void update(String roleName, String duties, boolean mine) {
        this.roleName = roleName;
        this.duties = duties;
        this.mine = mine;
    }

    public String getRoleName() { return roleName; }
    public String getDuties() { return duties; }
    public boolean isMine() { return mine; }
}
