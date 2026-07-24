package com.app.logutility.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The React SPA's client-side routes ({@code /}, {@code /login}, {@code /admin/**}) have no
 * matching static file, so a direct browser load or refresh (as opposed to an in-app navigation)
 * 404s unless forwarded to the SPA shell, which then resolves the route with its own router.
 * {@code /api/**}, actuator, and the OpenAPI/Swagger paths are real endpoints and aren't listed
 * here, so they fall through to their own handlers untouched.
 */
@Controller
public class SpaController {

    @GetMapping({"/", "/login", "/admin/**"})
    public String spaShell() {
        return "forward:/index.html";
    }
}
