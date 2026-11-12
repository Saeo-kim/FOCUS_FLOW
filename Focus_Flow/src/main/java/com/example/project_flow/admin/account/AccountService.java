package com.example.project_flow.admin.account;

import com.example.project_flow.admin.account.AccountDtos.AuthStatus;
import com.example.project_flow.admin.account.AccountDtos.LoginRequest;
import com.example.project_flow.admin.account.AccountDtos.SetupRequest;
import com.example.project_flow.admin.account.AccountDtos.UserResponse;
import com.example.project_flow.exception.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountService {

    private static final int MAX_FAILURES = 5;
    private static final long LOCK_MILLIS = 60_000;

    private final AppUserRepository users;
    private final PasswordHasher hasher;
    private final Clock clock;
    /** username -> {failures, lockedUntilMillis}; in memory, so a restart clears it. */
    private final Map<String, long[]> failures = new ConcurrentHashMap<>();

    public AccountService(AppUserRepository users, PasswordHasher hasher, Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AuthStatus status(Long sessionUserId) {
        boolean setupRequired = users.count() == 0;
        Optional<AppUser> user = sessionUserId == null ? Optional.empty() : users.findById(sessionUserId);
        return new AuthStatus(setupRequired, user.isPresent(), user.map(UserResponse::from).orElse(null));
    }

    public Optional<AppUser> find(Long id) {
        return id == null ? Optional.empty() : users.findById(id);
    }

    /** Creates the first administrator. Refused once any account exists. */
    @Transactional
    public synchronized AppUser setup(SetupRequest request) {
        if (users.count() > 0) {
            throw new ConflictException("The administrator account already exists. Sign in instead.");
        }
        AppUser admin = new AppUser(request.username().trim(), request.displayName().trim(),
                hasher.hash(request.password()), UserRole.ADMIN, LocalDateTime.now(clock));
        admin.signedIn(LocalDateTime.now(clock));
        return users.save(admin);
    }

    @Transactional
    public AppUser login(LoginRequest request) {
        String key = request.username().trim().toLowerCase(Locale.ROOT);
        long now = clock.millis();
        long[] state = failures.get(key);
        if (state != null && state[1] > now) {
            throw new ConflictException("Too many failed attempts. Wait a minute and try again.");
        }
        AppUser user = users.findByUsernameIgnoreCase(request.username().trim())
                .filter(u -> hasher.matches(request.password(), u.getPasswordHash()))
                .orElse(null);
        if (user == null) {
            failures.compute(key, (k, s) -> {
                long count = (s == null || s[1] != 0 && s[1] <= now ? 0 : s[0]) + 1;
                return new long[]{count, count >= MAX_FAILURES ? now + LOCK_MILLIS : 0};
            });
            throw new ConflictException("The username or password is incorrect.");
        }
        failures.remove(key);
        user.signedIn(LocalDateTime.now(clock));
        return user;
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

    private AppUser require(Long id) {
        return users.findById(id).orElseThrow(() -> new ConflictException("Your account no longer exists. Sign in again."));
    }
}
