package com.example.project_flow.admin.account;

import com.example.project_flow.admin.account.AccountDtos.AuthStatus;
import com.example.project_flow.admin.account.AccountDtos.InvitationRequest;
import com.example.project_flow.admin.account.AccountDtos.IssuedCode;
import com.example.project_flow.admin.account.AccountDtos.LoginRequest;
import com.example.project_flow.admin.account.AccountDtos.MemberCreateRequest;
import com.example.project_flow.admin.account.AccountDtos.MemberUpdateRequest;
import com.example.project_flow.admin.account.AccountDtos.SetupRequest;
import com.example.project_flow.admin.account.AccountDtos.UserResponse;
import com.example.project_flow.exception.ConflictException;
import com.example.project_flow.exception.NotFoundException;
import com.example.project_flow.service.ActivityLogService;
import com.example.project_flow.service.References;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountService {

    private static final int MAX_FAILURES = 5;
    private static final long LOCK_MILLIS = 60_000;
    private static final int CODE_DAYS = 7;
    /** No 0/O or 1/I, so a code read aloud or copied by hand is unambiguous. */
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final AppUserRepository users;
    private final PasswordHasher hasher;
    private final ActivityLogService activity;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    /** key -> {failures, lockedUntilMillis}; in memory, so a restart clears it. */
    private final Map<String, long[]> failures = new ConcurrentHashMap<>();

    public AccountService(AppUserRepository users, PasswordHasher hasher, ActivityLogService activity, Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.activity = activity;
        this.clock = clock;
    }

    public static String homeOf(AppUser user) {
        return user.getRole() == UserRole.ADMIN ? "/admin" : "/member";
    }

    @Transactional(readOnly = true)
    public AuthStatus status(Long sessionUserId) {
        boolean setupRequired = users.countByRole(UserRole.ADMIN) == 0;
        Optional<AppUser> user = activeUser(sessionUserId);
        return new AuthStatus(setupRequired, user.isPresent(), user.map(UserResponse::from).orElse(null),
                user.map(AccountService::homeOf).orElse("/login"));
    }

    /** The session's user, if it still exists and is still allowed to sign in. */
    public Optional<AppUser> activeUser(Long id) {
        return id == null ? Optional.empty() : users.findById(id).filter(AppUser::isActive);
    }

    /** Creates the first administrator. Refused once any administrator exists. */
    @Transactional
    public synchronized AppUser setup(SetupRequest request) {
        if (users.countByRole(UserRole.ADMIN) > 0) {
            throw new ConflictException("The administrator account already exists. Sign in instead.");
        }
        requireFreeUsername(request.username().trim(), null);
        AppUser admin = new AppUser(request.username().trim(), request.displayName().trim(),
                hasher.hash(request.password()), UserRole.ADMIN, LocalDateTime.now(clock));
        admin.signedIn(LocalDateTime.now(clock));
        return users.save(admin);
    }

    @Transactional
    public AppUser login(LoginRequest request) {
        String login = request.username().trim();
        String key = "login:" + login.toLowerCase(Locale.ROOT);
        guard(key);
        Optional<AppUser> found = login.contains("@") ? users.findByEmailIgnoreCase(login) : users.findByUsernameIgnoreCase(login);
        AppUser user = found.filter(u -> u.isPasswordSet() && hasher.matches(request.password(), u.getPasswordHash())).orElse(null);
        if (user == null) {
            fail(key);
            if (found.isPresent() && !found.get().isPasswordSet()) {
                throw new ConflictException("This account is waiting to be set up. Use your invitation code below.");
            }
            throw new ConflictException("The username or password is incorrect.");
        }
        if (!user.isActive()) {
            throw new ConflictException("This account has been deactivated. Contact your administrator.");
        }
        failures.remove(key);
        user.signedIn(LocalDateTime.now(clock));
        return user;
    }

    /** Uses an invitation or reset code to set the password, then signs the user in. */
    @Transactional
    public AppUser acceptCode(InvitationRequest request) {
        // Codes carry 60 random bits and expire in a week, so guessing is not a practical attack.
        String code = normalizeCode(request.code());
        AppUser user = code.length() == 12 ? users.findByInviteCodeHash(sha256(code)).orElse(null) : null;
        if (user == null) {
            throw new ConflictException("That code is not valid. Check it, or ask your administrator for a new one.");
        }
        if (user.getInviteExpiresAt() != null && user.getInviteExpiresAt().isBefore(LocalDateTime.now(clock))) {
            throw new ConflictException("That code has expired. Ask your administrator for a new one.");
        }
        if (!user.isActive()) {
            throw new ConflictException("This account has been deactivated. Contact your administrator.");
        }
        boolean firstTime = !user.isPasswordSet() && user.getLastLoginAt() == null;
        user.changePassword(hasher.hash(request.password()));
        user.signedIn(LocalDateTime.now(clock));
        activity.record(firstTime ? "ACCOUNT_JOINED" : "ACCOUNT_PASSWORD_RESET",
                user.getDisplayName() + (firstTime ? " joined with an invitation" : " set a new password with a reset code"));
        return user;
    }

    /* ---------- Administration ---------- */

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return users.findAllByOrderByRoleAscDisplayNameAsc().stream().map(UserResponse::from).toList();
    }

    public AppUser require(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("That account no longer exists."));
    }

    /** Creates an account that the person activates with the returned one-time code. */
    @Transactional
    public IssuedCode invite(MemberCreateRequest request) {
        String username = request.username().trim();
        String email = cleanEmail(request.email());
        requireFreeUsername(username, null);
        requireFreeEmail(email, null);
        UserRole role = request.role() == null ? UserRole.MEMBER : request.role();
        AppUser user = new AppUser(username, request.displayName().trim(), unusableHash(), role, LocalDateTime.now(clock));
        user.changeEmail(email);
        users.save(user);
        activity.record("ACCOUNT_INVITED", "Invited " + user.getDisplayName() + " (@" + username + ") as " + (role == UserRole.ADMIN ? "administrator" : "member"));
        return issue(user, true);
    }

    /** A new one-time code for someone who is locked out or never finished their invitation. */
    @Transactional
    public IssuedCode resetAccess(Long actorId, Long userId) {
        AppUser user = require(userId);
        if (Objects.equals(actorId, userId)) {
            throw new ConflictException("Change your own password from Account settings instead.");
        }
        activity.record("ACCOUNT_RESET", "Issued a new access code for " + user.getDisplayName() + " (@" + user.getUsername() + ")");
        return issue(user, true);
    }

    @Transactional
    public UserResponse update(Long actorId, Long userId, MemberUpdateRequest request) {
        AppUser user = require(userId);
        String email = cleanEmail(request.email());
        requireFreeEmail(email, userId);
        if (request.active() != null && !request.active() && user.isActive()) {
            if (Objects.equals(actorId, userId)) {
                throw new ConflictException("You cannot deactivate your own account.");
            }
            if (user.getRole() == UserRole.ADMIN && activeAdmins() <= 1) {
                throw new ConflictException("At least one administrator must stay active.");
            }
        }
        user.rename(request.displayName().trim());
        user.changeEmail(email);
        if (request.active() != null && request.active() != user.isActive()) {
            activity.record(request.active() ? "ACCOUNT_REACTIVATED" : "ACCOUNT_DEACTIVATED",
                    (request.active() ? "Reactivated " : "Deactivated ") + user.getDisplayName() + " (@" + user.getUsername() + ")");
            user.setActive(request.active());
        }
        return UserResponse.from(user);
    }

    @Transactional
    public AppUser rename(Long userId, String displayName) {
        AppUser user = require(userId);
        user.rename(displayName.trim());
        return user;
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        AppUser user = require(userId);
        if (!hasher.matches(currentPassword, user.getPasswordHash())) {
            throw new ConflictException("Your current password is incorrect.");
        }
        user.changePassword(hasher.hash(newPassword));
    }

    /* ---------- Helpers ---------- */

    private IssuedCode issue(AppUser user, boolean lockPassword) {
        String code = newCode();
        LocalDateTime expires = LocalDateTime.now(clock).plusDays(CODE_DAYS);
        user.issueCode(sha256(normalizeCode(code)), expires, lockPassword, unusableHash());
        return new IssuedCode(UserResponse.from(user), code, expires);
    }

    private long activeAdmins() {
        return users.findAll().stream().filter(u -> u.getRole() == UserRole.ADMIN && u.isActive()).count();
    }

    private void requireFreeUsername(String username, Long exceptId) {
        users.findByUsernameIgnoreCase(username).filter(u -> !u.getId().equals(exceptId)).ifPresent(u -> {
            throw new ConflictException("That username is already taken.");
        });
    }

    private void requireFreeEmail(String email, Long exceptId) {
        if (email == null) {
            return;
        }
        users.findByEmailIgnoreCase(email).filter(u -> !u.getId().equals(exceptId)).ifPresent(u -> {
            throw new ConflictException("That email is already used by another account.");
        });
    }

    private static String cleanEmail(String email) {
        String e = References.clean(email);
        return e == null ? null : e.toLowerCase(Locale.ROOT);
    }

    /** A hash no password can match, used while an account waits for its code. */
    private String unusableHash() {
        return "locked$" + newCode() + newCode();
    }

    private String newCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append('-');
            }
            sb.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void guard(String key) {
        long[] state = failures.get(key);
        if (state != null && state[1] > clock.millis()) {
            throw new ConflictException("Too many failed attempts. Wait a minute and try again.");
        }
    }

    private void fail(String key) {
        long now = clock.millis();
        failures.compute(key, (k, s) -> {
            long count = (s == null || s[1] != 0 && s[1] <= now ? 0 : s[0]) + 1;
            return new long[]{count, count >= MAX_FAILURES ? now + LOCK_MILLIS : 0};
        });
    }
}
