package com.in10s.logutility.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Protected admin landing page. Requiring an authenticated request here (enforced by
 * {@link com.in10s.logutility.security.SecurityConfig}) is what proves the login works in
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
