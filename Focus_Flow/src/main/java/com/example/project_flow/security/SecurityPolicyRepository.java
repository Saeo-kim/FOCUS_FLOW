package com.example.project_flow.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SecurityPolicyRepository extends JpaRepository<SecurityPolicy, Long> {
    List<SecurityPolicy> findAllByOrderByIdDesc();
    List<SecurityPolicy> findByPolicyKeyOrderByVersionDesc(String policyKey);
}
