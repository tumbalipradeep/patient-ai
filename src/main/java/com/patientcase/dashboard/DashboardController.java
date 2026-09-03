package com.patientcase.dashboard;

import com.patientcase.user.User;
import com.patientcase.user.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserService userService;

    public DashboardController(DashboardService dashboardService, UserService userService) {
        this.dashboardService = dashboardService;
        this.userService = userService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication authentication) {
        DashboardService.DashboardStats stats = dashboardService.getStats();
        model.addAttribute("stats", stats);

        try {
            User currentUser = userService.findByUsername(authentication.getName());
            model.addAttribute("currentUser", currentUser);
        } catch (Exception e) {
            // User may not exist in test context; not critical for rendering
        }

        return "dashboard/dashboard";
    }
}
