package com.example.project_flow.aiteam;

import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.admin.account.AppUserRepository;
import com.example.project_flow.exception.ConflictException;
import com.example.project_flow.integration.ownersoffice.OwnersOfficeClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Who the AI teammates are, and whether their outside application is connected. */
@Service
public class AiTeamService {

    public record Integration(boolean configured, boolean reachable, long openRequests) {
    }

    public record MemberView(String key, Long userId, String displayName, String username, String title,
                             String department, String avatar, String avatarColor, boolean active,
                             List<String> responsibilities, String integration, Integration status) {
    }

    private final AiTeamCatalog catalog;
    private final AppUserRepository users;
    private final AiRequestRepository requests;
    private final OwnersOfficeClient office;

    public AiTeamService(AiTeamCatalog catalog, AppUserRepository users, AiRequestRepository requests,
                         OwnersOfficeClient office) {
        this.catalog = catalog;
        this.users = users;
        this.requests = requests;
        this.office = office;
    }

    @Transactional(readOnly = true)
    public List<MemberView> members() {
        long open = requests.findByStatusIn(AiRequestStatus.OPEN).size();
        boolean reachable = office.reachable();
        return catalog.all().stream().map(m -> {
            AppUser seat = users.findByUsernameIgnoreCase(m.username()).orElse(null);
            return new MemberView(m.key(), seat == null ? null : seat.getId(), m.displayName(), m.username(), m.title(),
                    m.department().name(), m.avatar(), m.avatarColor(),
                    seat != null && seat.isActive(), m.responsibilities(), m.integration(),
                    new Integration(office.configured(), reachable, open));
        }).toList();
    }

    /** The seat for a teammate, if it exists and is active. */
    @Transactional(readOnly = true)
    public Optional<AppUser> activeSeat(String key) {
        return catalog.byKey(key)
                .flatMap(m -> users.findByUsernameIgnoreCase(m.username()))
                .filter(AppUser::isAi)
                .filter(AppUser::isActive);
    }

    public AppUser requireSeat(String key) {
        return activeSeat(key).orElseThrow(() -> new ConflictException(
                "That AI teammate is not available. Check the AI team page."));
    }
}
