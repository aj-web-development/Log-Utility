package com.in10s.logutility.controller.project;

import com.in10s.logutility.repository.project.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.in10s.logutility.entity.project.Project;
import com.in10s.logutility.request.project.NodeForm;
import com.in10s.logutility.request.project.ProjectWizardForm;
import com.in10s.logutility.service.project.ProjectService;

/**
 * Drives the HTMX wizard through a full create, carrying the same session (which holds the
 * wizard draft) across steps. MockMvc is built with {@code springSecurity()}; the Spring
 * Session servlet filter is not part of this chain, so the MockHttpSession carries normally.
 */
@SpringBootTest
class ProjectWizardControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectService projectService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void projectsAreProtected() throws Exception {
        mvc.perform(get("/admin/projects")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/projects/new")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listAndWizardStartRender() throws Exception {
        mvc.perform(get("/admin/projects")).andExpect(status().isOk());
        mvc.perform(get("/admin/projects/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Project details")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void wizardHappyPathCreatesProject() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(get("/admin/projects/new"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession();

        mvc.perform(post("/admin/projects/wizard/details").session(session).with(csrf())
                        .param("name", "wizard-created").param("description", "via wizard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h2 class=\"font-semibold\">Nodes</h2>")))
                // Regression check: th:switch/th:case must actually suppress the other steps —
                // a prior bug rendered every step's form on every response.
                .andExpect(content().string(not(containsString("Project details"))));

        mvc.perform(post("/admin/projects/wizard/nodes").session(session).with(csrf())
                        .param("nodes[0].nodeLabel", "node1")
                        .param("nodes[0].liveLogPath", "/var/log/app.log"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h2 class=\"font-semibold\">Sample line</h2>")))
                .andExpect(content().string(not(containsString("<h2 class=\"font-semibold\">Nodes</h2>"))))
                .andExpect(content().string(not(containsString("Project details"))));

        mvc.perform(post("/admin/projects/wizard/sample-line").session(session).with(csrf())
                        .param("linePattern.sampleLine", "2026-07-21 14:30:15.000 [main] INFO com.acme.App - hi"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h2 class=\"font-semibold\">Filter fields</h2>")))
                .andExpect(content().string(not(containsString("<h2 class=\"font-semibold\">Sample line</h2>"))));

        mvc.perform(post("/admin/projects/wizard/fields").session(session).with(csrf())
                        .param("filterFields[0].key", "tid")
                        .param("filterFields[0].label", "Trace ID")
                        .param("filterFields[0].matchType", "EXACT_TOKEN"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Review &amp; save")))
                .andExpect(content().string(not(containsString("<h2 class=\"font-semibold\">Filter fields</h2>"))))
                .andExpect(content().string(not(containsString("<h2 class=\"font-semibold\">Nodes</h2>"))));

        mvc.perform(post("/admin/projects/wizard/save").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect", "/admin/projects"));

        assertThat(projectRepository.findByName("wizard-created")).isPresent();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void analyzeSampleLineFillsSuggestedPatternsAndHighlightsPreview() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(get("/admin/projects/new"))
                .andReturn().getRequest().getSession();
        mvc.perform(post("/admin/projects/wizard/details").session(session).with(csrf())
                        .param("name", "analyze-test")).andReturn();
        mvc.perform(post("/admin/projects/wizard/nodes").session(session).with(csrf())
                        .param("nodes[0].nodeLabel", "node1")).andReturn();

        mvc.perform(post("/admin/projects/wizard/sample-line/analyze").session(session).with(csrf())
                        .param("linePattern.sampleLine",
                                "2026-07-21 14:30:15.123 [main] INFO com.acme.OrderService - order placed"))
                .andExpect(status().isOk())
                // Suggested patterns pre-fill the inputs.
                .andExpect(content().string(containsString("value=\"yyyy-MM-dd HH:mm:ss.SSS\"")))
                .andExpect(content().string(containsString(
                        "value=\"\\b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\\b\"")))
                // The highlighted preview reconstructs the sample line with labeled spans.
                .andExpect(content().string(containsString("bg-yellow-200")))
                .andExpect(content().string(containsString("com.acme.OrderService")))
                // Still on the Sample line step — Analyze does not advance the wizard.
                .andExpect(content().string(containsString("<h2 class=\"font-semibold\">Sample line</h2>")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void linePatternPersistsAcrossSaveAndEdit() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(get("/admin/projects/new"))
                .andReturn().getRequest().getSession();
        mvc.perform(post("/admin/projects/wizard/details").session(session).with(csrf())
                        .param("name", "line-pattern-test")).andReturn();
        mvc.perform(post("/admin/projects/wizard/nodes").session(session).with(csrf())
                        .param("nodes[0].nodeLabel", "node1")).andReturn();
        mvc.perform(post("/admin/projects/wizard/sample-line").session(session).with(csrf())
                        .param("linePattern.timestampPattern", "yyyy-MM-dd HH:mm:ss.SSS")
                        .param("linePattern.levelPattern", "\\b(INFO|WARN|ERROR)\\b")
                        .param("linePattern.loggerPattern", "com\\.acme\\..*"))
                .andReturn();
        mvc.perform(post("/admin/projects/wizard/fields").session(session).with(csrf())).andReturn();
        mvc.perform(post("/admin/projects/wizard/save").session(session).with(csrf()))
                .andExpect(header().string("HX-Redirect", "/admin/projects"));

        UUID projectId = projectRepository.findByName("line-pattern-test").orElseThrow().getId();
        ProjectWizardForm reloaded = projectService.loadForEdit(projectId);
        assertThat(reloaded.getLinePattern().getTimestampPattern()).isEqualTo("yyyy-MM-dd HH:mm:ss.SSS");
        assertThat(reloaded.getLinePattern().getLevelPattern()).isEqualTo("\\b(INFO|WARN|ERROR)\\b");
        assertThat(reloaded.getLinePattern().getLoggerPattern()).isEqualTo("com\\.acme\\..*");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void detailsStepRejectsDuplicateName() throws Exception {
        ProjectWizardForm existing = new ProjectWizardForm();
        existing.setName("already-there");
        NodeForm node = new NodeForm();
        node.setNodeLabel("n1");
        existing.getNodes().add(node);
        projectService.saveFromWizard(existing);

        MockHttpSession session = (MockHttpSession) mvc.perform(get("/admin/projects/new"))
                .andReturn().getRequest().getSession();

        mvc.perform(post("/admin/projects/wizard/details").session(session).with(csrf())
                        .param("name", "already-there"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("already exists")));
    }
}
