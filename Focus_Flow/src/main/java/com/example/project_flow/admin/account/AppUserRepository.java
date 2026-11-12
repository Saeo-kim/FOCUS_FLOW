package com.example.project_flow.admin.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findByInviteCodeHash(String inviteCodeHash);

    List<AppUser> findAllByOrderByRoleAscDisplayNameAsc();

    long countByRole(UserRole role);
}
