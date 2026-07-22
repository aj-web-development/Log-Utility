package com.app.logutility.controller.project;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.app.logutility.entity.project.Project;

/** Drives a real logback-spring.xml upload through the wizard, verifying the seeded draft. */
@SpringBootTest
class ProjectUploadControllerTest {

    private static final String SAMPLE_XML = """
            <configuration>
                <springProperty name="LOG_PATH" source="logging.path" defaultValue="/var/log/orders"/>
                <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
                    <file>${LOG_PATH}/app.log</file>
                    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
                        <fileNamePattern>${LOG_PATH}/archive/app.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
                    </rollingPolicy>
                    <encoder>
                        <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} tid=%X{traceId} - %msg%n</pattern>
                    </encoder>
                </appender>
            </configuration>
            """;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void uploadIsProtected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "logback-spring.xml", "application/xml",
                SAMPLE_XML.getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/admin/projects/upload").file(file).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void uploadSeedsWizardDraftWithPrefilledPatternAndFields() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "logback-spring.xml", "application/xml",
                SAMPLE_XML.getBytes(StandardCharsets.UTF_8));

        MockHttpSession session = (MockHttpSession) mvc.perform(multipart("/admin/projects/upload")
                        .file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Project details")))
                .andReturn().getRequest().getSession();

        // Advance to NODES: the backup pattern converted from the XML should already be filled in,
        // and only the Nodes step's own content should be present (see the th:switch/th:case
        // precedence regression covered by ProjectWizardControllerTest).
        mvc.perform(post("/admin/projects/wizard/details").session(session).with(csrf())
                        .param("name", "uploaded-project"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("app.{date}.{i}.log.gz")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/var/log/orders/app.log")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<h2 class=\"font-semibold\">Project details</h2>"))));

        // Advance past NODES (now lands on SAMPLE_LINE), then Next to FIELDS: the traceId MDC
        // field should already be filled in.
        mvc.perform(post("/admin/projects/wizard/nodes").session(session).with(csrf())
                        .param("nodes[0].nodeLabel", "node1")
                        .param("nodes[0].backupPathPattern", "app.{date}.{i}.log.gz"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<h2 class=\"font-semibold\">Sample line</h2>")));

        mvc.perform(post("/admin/projects/wizard/sample-line").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"traceId\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"tid=\"")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void malformedUploadShowsErrorOnUploadPage() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "bad.xml", "application/xml",
                "<not-valid-xml".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/admin/projects/upload").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Could not parse")));
    }
}
