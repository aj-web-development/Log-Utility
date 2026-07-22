package com.in10s.logutility.web;

import com.in10s.logutility.project.config.ProjectAdminController;
import com.in10s.logutility.project.config.ProjectService;
import com.in10s.logutility.project.config.PublicProjectView;
import com.in10s.logutility.search.SearchRequest;
import com.in10s.logutility.search.SearchResult;
import com.in10s.logutility.search.SearchService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The public, unauthenticated search UI: a single page with a project switcher, a filter form
 * whose fields are rendered dynamically from the active project's {@code FilterField} list, and a
 * results table. The active project is stored in a cookie (not the login session) since searching
 * requires no login. Only {@link #search} is fluid/HTMX-driven — switching projects is a plain
 * navigation, which is fine since only the search submit itself needs to feel instant.
 */
@Controller
@RequiredArgsConstructor
public class SearchController {

    private static final int PAGE_SIZE = 50;
    private static final String FILTER_PARAM_PREFIX = "filter_";

    private final ProjectService projectService;
    private final SearchService searchService;

    @GetMapping("/")
    public String index(@CookieValue(name = ProjectAdminController.ACTIVE_PROJECT_COOKIE, required = false)
                         String activeProjectId, Model model) {
        model.addAttribute("projects", projectService.listProjects());

        PublicProjectView activeProject = StringUtils.hasText(activeProjectId)
                ? parseUuid(activeProjectId).flatMap(projectService::findPublicView).orElse(null)
                : null;
        model.addAttribute("activeProject", activeProject);

        LocalDateTime now = LocalDateTime.now();
        model.addAttribute("defaultFrom", now.minusDays(1));
        model.addAttribute("defaultTo", now);
        return "search";
    }

    @GetMapping("/search/select-project")
    public String selectProject(@RequestParam UUID projectId, HttpServletResponse response) {
        Cookie cookie = new Cookie(ProjectAdminController.ACTIVE_PROJECT_COOKIE, projectId.toString());
        cookie.setPath("/");
        cookie.setMaxAge((int) Duration.ofDays(30).toSeconds());
        response.addCookie(cookie);
        return "redirect:/";
    }

    @GetMapping("/search")
    public String search(@CookieValue(name = ProjectAdminController.ACTIVE_PROJECT_COOKIE, required = false)
                         String activeProjectId,
                         @RequestParam(required = false) String from,
                         @RequestParam(required = false) String to,
                         @RequestParam(required = false) String freeText,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam Map<String, String> allParams,
                         Model model) {
        Optional<UUID> projectId = StringUtils.hasText(activeProjectId) ? parseUuid(activeProjectId) : Optional.empty();
        if (projectId.isEmpty()) {
            model.addAttribute("error", "Select a project above before searching.");
            return "search :: resultsFragment";
        }

        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            if (entry.getKey().startsWith(FILTER_PARAM_PREFIX) && StringUtils.hasText(entry.getValue())) {
                filters.put(entry.getKey().substring(FILTER_PARAM_PREFIX.length()), entry.getValue());
            }
        }

        SearchResult result = searchService.search(new SearchRequest(
                projectId.get(), parseDateTime(from), parseDateTime(to), filters, freeText, page, PAGE_SIZE));

        model.addAttribute("result", result);
        model.addAttribute("page", page);
        model.addAttribute("hasPrev", page > 0);
        model.addAttribute("hasNext", (long) (page + 1) * PAGE_SIZE < result.totalMatched());
        return "search :: resultsFragment";
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty(); // stale/malformed cookie — treat as no active project
        }
    }

    private static LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null; // SearchService applies its own sensible default
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
