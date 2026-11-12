package com.example.project_flow.admin.web;

import com.example.project_flow.admin.account.AccountService;
import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.admin.account.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The three entry points of one application. Each page is sent only to the role it belongs to;
 * anyone else is redirected to the login page or to their own workspace.
 */
@Controller
public class AdminPageController {

    private final AccountService accounts;

    public AdminPageController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/")
    public String root(HttpServletRequest request) {
        return accounts.activeUser(UserSession.userId(request))
                .map(user -> "redirect:" + AccountService.homeOf(user))
                .orElse("redirect:/login");
    }

    @GetMapping({"/login", "/login/"})
    public String login(HttpServletRequest request) {
        return accounts.activeUser(UserSession.userId(request))
                .map(user -> "redirect:" + AccountService.homeOf(user))
                .orElse("forward:/login.html");
    }

    @GetMapping({"/admin", "/admin/", "/admin-dashboard", "/admin-dashboard/"})
    public String admin(HttpServletRequest request) {
        return page(request, UserRole.ADMIN, "forward:/admin-dashboard.html");
    }

    @GetMapping({"/member", "/member/"})
    public String member(HttpServletRequest request) {
        return page(request, UserRole.MEMBER, "forward:/member.html");
    }

    private String page(HttpServletRequest request, UserRole role, String view) {
        AppUser user = accounts.activeUser(UserSession.userId(request)).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }
        return user.getRole() == role ? view : "redirect:" + AccountService.homeOf(user);
    }
}
