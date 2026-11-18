package com.example.project_flow.legal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LegalOwnerAppointmentRepository extends JpaRepository<LegalOwnerAppointment, Long> {
    List<LegalOwnerAppointment> findByRevokedAtIsNullOrderByIdDesc();
    List<LegalOwnerAppointment> findAllByOrderByIdAsc();
}
