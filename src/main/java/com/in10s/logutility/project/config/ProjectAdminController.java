package com.in10s.logutility.project.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Duration;
import java.util.UUID;

/**
 * Admin project list / switcher. The "active project" for (public) search is stored in a cookie
 * rather than the admin session, since searching itself is public and not tied to a login.
 * Delete and activate return just the list fragment so HTMX can swap it in place.
 */
@Controller
@RequestMapping("/admin/projects")
@RequiredArgsConstructor
public class ProjectAdminController {

    /** Cookie holding the active project id for the search UI (read publicly in Phase 7). */
    public static final String ACTIVE_PROJECT_COOKIE = "LOGUTY_ACTIVE_PROJECT";

    private final ProjectService projectService;

    @GetMapping
    public String list(@CookieValue(name = ACTIVE_PROJECT_COOKIE, required = false) String activeProjectId,
                       Model model) {
        populate(model, activeProjectId);
        return "admin/projects/list";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id,
                         @CookieValue(name = ACTIVE_PROJECT_COOKIE, required = false) String activeProjectId,
                         HttpServletResponse response, Model model) {
        projectService.deleteProject(id);
        String active = activeProjectId;
        if (id.toString().equals(activeProjectId)) {
            response.addCookie(expiredActiveCookie()); // the active project no longer exists
            active = null;
        }
        populate(model, active);
        return "admin/projects/list :: projectList";
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable UUID id, HttpServletResponse response, Model model) {
        Cookie cookie = new Cookie(ACTIVE_PROJECT_COOKIE, id.toString());
        cookie.setPath("/");
        cookie.setMaxAge((int) Duration.ofDays(30).toSeconds());
        response.addCookie(cookie);
        populate(model, id.toString());
        return "admin/projects/list :: projectList";
    }

    private void populate(Model model, String activeProjectId) {
        model.addAttribute("projects", projectService.listProjects());
        model.addAttribute("activeProjectId", activeProjectId);
    }

    private Cookie expiredActiveCookie() {
        Cookie cookie = new Cookie(ACTIVE_PROJECT_COOKIE, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        return cookie;
    }
}
