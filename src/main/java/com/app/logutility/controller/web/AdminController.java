package com.app.logutility.controller.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.app.logutility.config.security.SecurityConfig;

/**
 * Protected admin landing page. Requiring an authenticated request here (enforced by
 * {@link SecurityConfig}) is what proves the login works in
 * Phase 2; project configuration and the wizard are added on top of it in Phase 3.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    @GetMapping
    public String index(Authentication authentication, Model model) {
        model.addAttribute("username", authentication.getName());
        return "admin/index";
    }
}
