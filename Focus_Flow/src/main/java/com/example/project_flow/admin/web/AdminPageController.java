package com.example.project_flow.admin.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the admin edition of FOCUS FLOW at its own address. The original page stays at "/". */
@Controller
public class AdminPageController {

    @GetMapping({"/admin-dashboard", "/admin-dashboard/"})
    public String adminDashboard() {
        return "forward:/admin-dashboard.html";
    }
}
