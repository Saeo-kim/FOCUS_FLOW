package com.example.project_flow.admin.web;

import com.example.project_flow.admin.account.AccountDtos.AuthStatus;
import com.example.project_flow.admin.account.AccountDtos.InvitationRequest;
import com.example.project_flow.admin.account.AccountDtos.LoginRequest;
import com.example.project_flow.admin.account.AccountDtos.PasswordRequest;
import com.example.project_flow.admin.account.AccountDtos.ProfileRequest;
import com.example.project_flow.admin.account.AccountDtos.SetupRequest;
import com.example.project_flow.admin.account.AccountDtos.UserResponse;
import com.example.project_flow.admin.account.AccountService;
import com.example.project_flow.admin.account.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sign-in for both roles. /api/admin/auth is the address used before members existed and still works. */
@RestController
@RequestMapping({"/api/auth", "/api/admin/auth"})
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/status")
    public AuthStatus status(HttpServletRequest request) {
        return accounts.status(UserSession.userId(request));
    }

    @PostMapping("/setup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthStatus setup(@Valid @RequestBody SetupRequest body, HttpServletRequest request) {
        return signIn(request, accounts.setup(body));
    }

    @PostMapping("/login")
    public AuthStatus login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return signIn(request, accounts.login(body));
    }

    /** Activates an invitation, or finishes a password reset, with the one-time code. */
    @PostMapping("/invitation")
    public AuthStatus invitation(@Valid @RequestBody InvitationRequest body, HttpServletRequest request) {
        return signIn(request, accounts.acceptCode(body));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        UserSession.signOut(request);
    }

    @PutMapping("/profile")
    public UserResponse profile(@Valid @RequestBody ProfileRequest body, HttpServletRequest request) {
        return UserResponse.from(accounts.rename(UserSession.user(request).getId(), body.displayName()));
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@Valid @RequestBody PasswordRequest body, HttpServletRequest request) {
        accounts.changePassword(UserSession.user(request).getId(), body.currentPassword(), body.newPassword());
    }

    private AuthStatus signIn(HttpServletRequest request, AppUser user) {
        UserSession.signIn(request, user);
        return accounts.status(user.getId());
    }
}
