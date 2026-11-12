package com.example.project_flow.admin.web;

import com.example.project_flow.admin.account.AccountDtos.AuthStatus;
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

@RestController
@RequestMapping("/api/admin/auth")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/status")
    public AuthStatus status(HttpServletRequest request) {
        return accounts.status(AdminSession.userId(request));
    }

    @PostMapping("/setup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthStatus setup(@Valid @RequestBody SetupRequest body, HttpServletRequest request) {
        AppUser admin = accounts.setup(body);
        AdminSession.signIn(request, admin);
        return accounts.status(admin.getId());
    }

    @PostMapping("/login")
    public AuthStatus login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        AppUser user = accounts.login(body);
        AdminSession.signIn(request, user);
        return accounts.status(user.getId());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        AdminSession.signOut(request);
    }

    @PutMapping("/profile")
    public UserResponse profile(@Valid @RequestBody ProfileRequest body, HttpServletRequest request) {
        return UserResponse.from(accounts.rename(AdminSession.user(request).getId(), body.displayName()));
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@Valid @RequestBody PasswordRequest body, HttpServletRequest request) {
        accounts.changePassword(AdminSession.user(request).getId(), body.currentPassword(), body.newPassword());
    }
}
