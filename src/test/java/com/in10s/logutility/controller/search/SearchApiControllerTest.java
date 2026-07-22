package com.in10s.logutility.controller.search;

import tools.jackson.databind.ObjectMapper;
import com.in10s.logutility.entity.project.MatchType;
import com.in10s.logutility.request.project.FilterFieldForm;
import com.in10s.logutility.request.project.NodeForm;
import com.in10s.logutility.request.project.ProjectWizardForm;
import com.in10s.logutility.service.project.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST equivalent of {@link SearchControllerTest}: public, no authentication, JSON in/out
 * instead of cookies/HTML fragments.
 */
@SpringBootTest
class SearchApiControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void listProjectsIsPublicAndIncludesSeededProject() throws Exception {
        UUID projectId = createProject("search-api-list", null);

        mvc.perform(get("/api/search/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + projectId + "')]").exists());
    }

    @Test
    void getProjectReturnsPublicViewWithFilterFields() throws Exception {
        UUID projectId = createProject("search-api-detail", null);

        mvc.perform(get("/api/search/projects/{id}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", startsWith("search-api-detail")))
                .andExpect(jsonPath("$.fields[0].key").value("tid"))
                .andExpect(jsonPath("$.fields[0].label").value("Trace ID"));
    }

    @Test
    void getMissingProjectReturnsJson404() throws Exception {
        mvc.perform(get("/api/search/projects/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("not found")));
    }

    @Test
    void searchWithoutProjectIdReturns400() throws Exception {
        mvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"page\":0,\"pageSize\":50}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("projectId")));
    }

    @Test
    void searchReturnsMatchingLine(@TempDir Path tempDir) throws Exception {
        Path liveLog = tempDir.resolve("app.log");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Files.writeString(liveLog, formatted(now) + " INFO tid=api123 hello from search api\n");
        UUID projectId = createProject("search-api-live", liveLog.toString());

        Map<String, Object> body = Map.of(
                "projectId", projectId.toString(),
                "from", now.minusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "to", now.plusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "filters", Map.of("tid", "api123"),
                "page", 0,
                "pageSize", 50);

        mvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].raw", containsString("hello from search api")))
                .andExpect(jsonPath("$.totalMatched").value(1));
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
            node.setLiveLogPath(liveLogPath);
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
