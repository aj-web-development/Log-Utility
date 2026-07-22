package com.in10s.logutility.controller.project;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST equivalent of the admin project wizard/list: HTTP Basic protected, no session or CSRF
 * (the {@code /api/**} chain is stateless — every request here omits {@code with(csrf())}, which
 * doubles as a regression check that CSRF really is disabled for this chain).
 */
@SpringBootTest
class ProjectApiControllerTest {

    private static final String ADMIN = "admin";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void everyEndpointRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/projects/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/projects/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/projects/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/projects/sample-line/analyze")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/projects/path-check")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(get("/api/projects").with(httpBasic(ADMIN, "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getOnPostOnlyRouteReturns405NotMisroutedToIdLookup() throws Exception {
        // /path-check and /{id} sit at the same path depth; without the {id} UUID constraint,
        // GET /api/projects/path-check silently matched {id}="path-check" instead of 405-ing.
        // Body assertions aren't possible here: MockMvc doesn't perform the servlet-container
        // /error forward a real deployed Tomcat does, so ApiErrorAttributes never runs in this
        // test environment (verified separately, live, that the body carries the ApiError shape).
        mvc.perform(get("/api/projects/path-check").with(httpBasic(ADMIN, ADMIN)))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void malformedIdReturns404() throws Exception {
        // No controller method matches a non-UUID-shaped id (see the {id} UUID constraint on
        // this controller). See the comment above re: MockMvc and the /error forward.
        mvc.perform(get("/api/projects/not-a-uuid").with(httpBasic(ADMIN, ADMIN)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRejectsMissingName() throws Exception {
        mvc.perform(post("/api/projects").with(httpBasic(ADMIN, ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Project name is required."));
    }

    @Test
    void createRejectsMissingLabeledNode() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", "no-nodes-" + UUID.randomUUID()));
        mvc.perform(post("/api/projects").with(httpBasic(ADMIN, ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add at least one node with a label."));
    }

    @Test
    void fullLifecycleCreateGetListUpdateDeleteAndDuplicateConflict() throws Exception {
        String name = "api-lifecycle-" + UUID.randomUUID();
        Map<String, Object> createBody = Map.of(
                "name", name,
                "description", "created by test",
                "nodes", List.of(Map.of("nodeLabel", "node1", "liveLogPath", "/var/log/app.log")),
                "filterFields", List.of(Map.of("key", "tid", "label", "Trace ID", "matchType", "EXACT_TOKEN")));

        String createResponse = mvc.perform(post("/api/projects").with(httpBasic(ADMIN, ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.nodes[0].nodeLabel").value("node1"))
                .andExpect(jsonPath("$.filterFields[0].key").value("tid"))
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResponse).get("id").asString());

        mvc.perform(get("/api/projects/{id}", id).with(httpBasic(ADMIN, ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filterFields[0].key").value("tid"));

        mvc.perform(get("/api/projects").with(httpBasic(ADMIN, ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')]").exists());

        // Duplicate name on create -> 409, not 400 or 500.
        Map<String, Object> duplicate = Map.of("name", name, "nodes", List.of(Map.of("nodeLabel", "x")));
        mvc.perform(post("/api/projects").with(httpBasic(ADMIN, ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict());

        // Update: rename, keep one node.
        Map<String, Object> updateBody = Map.of(
                "name", name + "-renamed",
                "nodes", List.of(Map.of("nodeLabel", "node1-renamed")));
        mvc.perform(put("/api/projects/{id}", id).with(httpBasic(ADMIN, ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name + "-renamed"))
                .andExpect(jsonPath("$.nodes[0].nodeLabel").value("node1-renamed"))
                .andExpect(jsonPath("$.filterFields").isEmpty());

        // Delete, then confirm gone; deleting again is an idempotent no-op (204, not 404).
        mvc.perform(delete("/api/projects/{id}", id).with(httpBasic(ADMIN, ADMIN)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/projects/{id}", id).with(httpBasic(ADMIN, ADMIN)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/projects/{id}", id).with(httpBasic(ADMIN, ADMIN)))
                .andExpect(status().isNoContent());
    }

    @Test
    void sampleLineAnalyzeReturnsSuggestedPatterns() throws Exception {
        String body = "{\"sampleLine\":\"2026-07-22 06:49:00.123 INFO com.example.Foo - hi\"}";
        mvc.perform(post("/api/projects/sample-line/analyze").with(httpBasic(ADMIN, ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedTimestampPattern").value("yyyy-MM-dd HH:mm:ss.SSS"));
    }

    @Test
    void pathCheckReportsUnreachableForMissingPath() throws Exception {
        String body = "{\"livePath\":\"Z:/definitely/not/here.log\"}";
        mvc.perform(post("/api/projects/path-check").with(httpBasic(ADMIN, ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNREACHABLE"));
    }

    @Test
    void logbackParseExtractsMdcFieldsAndBackupPattern() throws Exception {
        String xml = """
                <configuration>
                  <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
                    <file>/var/log/app.log</file>
                    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
                      <fileNamePattern>/var/log/backup/app.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
                    </rollingPolicy>
                    <encoder><pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} tid=%X{traceId} - %msg%n</pattern></encoder>
                  </appender>
                </configuration>
                """;
        MockMultipartFile file = new MockMultipartFile("file", "logback-spring.xml", "application/xml",
                xml.getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/projects/logback/parse").file(file).with(httpBasic(ADMIN, ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mdcFields[0].mdcKey").value("traceId"))
                .andExpect(jsonPath("$.backupPathPattern").value("app.{date}.{i}.log.gz"));
    }

    @Test
    void logbackParseRejectsEmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.xml", "application/xml", new byte[0]);
        mvc.perform(multipart("/api/projects/logback/parse").file(file).with(httpBasic(ADMIN, ADMIN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logbackParseRejectsMalformedXml() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "bad.xml", "application/xml",
                "not xml".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/projects/logback/parse").file(file).with(httpBasic(ADMIN, ADMIN)))
                .andExpect(status().isBadRequest());
    }
}
