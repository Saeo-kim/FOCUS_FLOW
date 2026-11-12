package com.example.project_flow.admin.web;

import com.example.project_flow.admin.account.AccountDtos.IssuedCode;
import com.example.project_flow.admin.account.AccountDtos.MemberCreateRequest;
import com.example.project_flow.admin.account.AccountDtos.MemberUpdateRequest;
import com.example.project_flow.admin.account.AccountDtos.UserResponse;
import com.example.project_flow.admin.account.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Accounts, managed by an administrator. New people join through a one-time invitation code. */
@RestController
@RequestMapping("/api/admin/users")
public class MemberAdminController {

    private final AccountService accounts;

    public MemberAdminController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public List<UserResponse> list() {
        return accounts.listUsers();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssuedCode invite(@Valid @RequestBody MemberCreateRequest body) {
        return accounts.invite(body);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody MemberUpdateRequest body, HttpServletRequest request) {
        return accounts.update(UserSession.user(request).getId(), id, body);
    }

    @PostMapping("/{id}/reset-access")
    public IssuedCode resetAccess(@PathVariable Long id, HttpServletRequest request) {
        return accounts.resetAccess(UserSession.user(request).getId(), id);
    }
}
