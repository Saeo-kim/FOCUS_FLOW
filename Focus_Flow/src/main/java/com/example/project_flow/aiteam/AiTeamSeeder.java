package com.example.project_flow.aiteam;

import com.example.project_flow.admin.account.AccountKind;
import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.admin.account.AppUserRepository;
import com.example.project_flow.admin.account.UserRole;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Gives every teammate in {@link AiTeamCatalog} a seat in the member list, once. An existing seat
 * is left as it is, so a name or title changed by hand is kept. Seats never get a password.
 */
@Component
@Order(2)
public class AiTeamSeeder implements ApplicationRunner {

    private final AiTeamCatalog catalog;
    private final AppUserRepository users;
    private final Clock clock;

    public AiTeamSeeder(AiTeamCatalog catalog, AppUserRepository users, Clock clock) {
        this.catalog = catalog;
        this.users = users;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (AiTeamMember member : catalog.all()) {
            if (users.findByUsernameIgnoreCase(member.username()).isPresent()) {
                continue;
            }
            AppUser seat = new AppUser(member.username(), member.displayName(), "locked$ai$" + UUID.randomUUID(),
                    UserRole.MEMBER, LocalDateTime.now(clock));
            seat.markAiSeat("locked$ai$" + UUID.randomUUID());
            seat.assignJob(AccountKind.AI, member.title(), member.department());
            seat.changeAvatar(member.avatar(), member.avatarColor());
            users.save(seat);
        }
    }
}
