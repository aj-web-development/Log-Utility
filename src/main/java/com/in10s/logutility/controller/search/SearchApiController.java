package com.in10s.logutility.controller.search;

import com.in10s.logutility.exception.project.ProjectNotFoundException;
import com.in10s.logutility.request.search.SearchRequest;
import com.in10s.logutility.response.project.ProjectSummaryDto;
import com.in10s.logutility.response.project.PublicProjectView;
import com.in10s.logutility.response.search.SearchResult;
import com.in10s.logutility.service.project.ProjectService;
import com.in10s.logutility.service.search.SearchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The public, unauthenticated REST search API — the JSON equivalent of {@link SearchController},
 * for any external UI that wants to build its own search experience against this app. Reuses
 * {@link ProjectService} and {@link SearchService} directly; no new business logic here.
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Public: list configured projects and search their logs")
public class SearchApiController {

    private final ProjectService projectService;
    private final SearchService searchService;

    @GetMapping("/projects")
    public List<ProjectSummaryDto> listProjects() {
        return projectService.listProjects();
    }

    @GetMapping("/projects/{id}")
    public PublicProjectView getProject(@PathVariable UUID id) {
        return projectService.findPublicView(id).orElseThrow(() -> new ProjectNotFoundException(id));
    }

    @PostMapping
    public SearchResult search(@RequestBody SearchRequest request) {
        if (request.projectId() == null) {
            throw new IllegalArgumentException("projectId is required");
        }
        return searchService.search(request);
    }
}
