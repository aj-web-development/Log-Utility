package com.app.logutility.controller.search;

import com.app.logutility.entity.project.MatchType;
import com.app.logutility.request.project.FilterFieldForm;
import com.app.logutility.request.project.LogFileForm;
import com.app.logutility.request.project.NodeForm;
import com.app.logutility.controller.project.ProjectAdminController;
import com.app.logutility.service.project.ProjectService;
import com.app.logutility.request.project.ProjectWizardForm;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises the public, unauthenticated search UI end to end against real files on disk. */
@SpringBootTest
class SearchControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProjectService projectService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void homePageIsPublicAndShowsEmptyStateWithNoProjects() throws Exception {
        // A fresh H2 schema per test class run isn't guaranteed empty across the whole suite,
        // but the page must render successfully regardless of project count.
        mvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void selectingAProjectSetsCookieAndRedirects() throws Exception {
        UUID projectId = createProject("search-ui-switch", null);

        mvc.perform(get("/search/select-project").param("projectId", projectId.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(cookie().value(ProjectAdminController.ACTIVE_PROJECT_COOKIE, projectId.toString()));
    }

    @Test
    void homePageRendersDynamicFilterFieldsForActiveProject() throws Exception {
        UUID projectId = createProject("search-ui-fields", null);

        mvc.perform(get("/").cookie(new Cookie(ProjectAdminController.ACTIVE_PROJECT_COOKIE, projectId.toString())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("filter_tid")))
                .andExpect(content().string(containsString("Trace ID")));
    }

    @Test
    void searchWithoutActiveProjectShowsError() throws Exception {
        mvc.perform(get("/search")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Select a project")));
    }

    @Test
    void searchReturnsMatchingLineAndOmitsOtherStepsContent(@org.junit.jupiter.api.io.TempDir Path tempDir)
            throws Exception {
        Path liveLog = tempDir.resolve("app.log");
        LocalDateTime now = LocalDateTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0);
        Files.writeString(liveLog, formatted(now) + " INFO tid=web123 hello from search ui\n");

        UUID projectId = createProject("search-ui-live", liveLog.toString());

        mvc.perform(get("/search")
                        .cookie(new Cookie(ProjectAdminController.ACTIVE_PROJECT_COOKIE, projectId.toString()))
                        .param("from", now.minusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .param("to", now.plusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .param("filter_tid", "web123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hello from search ui")))
                .andExpect(content().string(containsString("1 matches")))
                // Regression check akin to the wizard's th:switch bug: the initial "enter your
                // criteria" placeholder belongs to the full page, not the results fragment.
                .andExpect(content().string(not(containsString("Enter your criteria"))));
    }

    @Test
    void searchWithNoMatchesShowsEmptyState() throws Exception {
        UUID projectId = createProject("search-ui-empty", null);

        mvc.perform(get("/search")
                        .cookie(new Cookie(ProjectAdminController.ACTIVE_PROJECT_COOKIE, projectId.toString()))
                        .param("freeText", "nothing-will-match-this"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No results found")));
    }

    private static String formatted(LocalDateTime dt) {
        return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    }

    private UUID createProject(String name, String liveLogPath) {
        ProjectWizardForm form = new ProjectWizardForm();
        form.setName(name + "-" + UUID.randomUUID());
        NodeForm node = new NodeForm();
        node.setNodeLabel("node1");
        if (liveLogPath != null) {
            LogFileForm output = new LogFileForm();
            output.setLiveLogPath(liveLogPath);
            node.getLogFiles().add(output);
        }
        form.getNodes().add(node);

        FilterFieldForm field = new FilterFieldForm();
        field.setKey("tid");
        field.setLabel("Trace ID");
        field.setMatchType(MatchType.EXACT_TOKEN);
        field.setLinePrefix("tid=");
        form.getFilterFields().add(field);

        return projectService.saveFromWizard(form);
    }
}
