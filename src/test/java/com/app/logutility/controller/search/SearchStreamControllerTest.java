package com.app.logutility.controller.search;

import com.app.logutility.entity.project.MatchType;
import com.app.logutility.request.project.FilterFieldForm;
import com.app.logutility.request.project.LogFileForm;
import com.app.logutility.request.project.NodeForm;
import com.app.logutility.request.project.ProjectWizardForm;
import com.app.logutility.service.project.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SSE variant of {@link SearchApiControllerTest}: chunk/done events instead of one JSON body. */
@SpringBootTest
class SearchStreamControllerTest {

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
    void streamsAMatchAsAChunkFollowedByADoneEvent(@TempDir Path tempDir) throws Exception {
        Path liveLog = tempDir.resolve("app.log");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Files.writeString(liveLog, formatted(now) + " INFO tid=stream123 hello from sse\n");
        UUID projectId = createProject("search-stream-live", liveLog.toString());

        MvcResult mvcResult = mvc.perform(get("/api/search/stream")
                        .param("projectId", projectId.toString())
                        .param("from", now.minusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .param("to", now.plusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .param("filter_tid", "stream123"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvcResult.getAsyncResult(5000);

        mvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:chunk")))
                .andExpect(content().string(containsString("hello from sse")))
                .andExpect(content().string(containsString("event:progress")))
                .andExpect(content().string(containsString("\"nodesCompleted\":1")))
                .andExpect(content().string(containsString("event:done")))
                .andExpect(content().string(containsString("\"totalMatched\":1")));
    }

    @Test
    void streamCompletesWithJustADoneEventWhenNothingMatches(@TempDir Path tempDir) throws Exception {
        // A configured-but-unreachable output, so a scan unit (and its progress event) still exists.
        UUID projectId = createProject("search-stream-empty", tempDir.resolve("does-not-exist.log").toString());

        MvcResult mvcResult = mvc.perform(get("/api/search/stream")
                        .param("projectId", projectId.toString())
                        .param("freeText", "nothing-will-match-this"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvcResult.getAsyncResult(5000);

        mvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:progress")))
                .andExpect(content().string(containsString("event:done")))
                .andExpect(content().string(containsString("\"totalMatched\":0")));
    }

    @Test
    void exportStreamsMatchedLinesAsAnAttachment(@TempDir Path tempDir) throws Exception {
        Path liveLog = tempDir.resolve("app.log");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Files.writeString(liveLog, formatted(now) + " INFO tid=export123 hello from export\n");
        UUID projectId = createProject("search-export-live", liveLog.toString());

        mvc.perform(get("/api/search/export")
                        .param("projectId", projectId.toString())
                        .param("from", now.minusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .param("to", now.plusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .param("filter_tid", "export123"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().string(containsString("hello from export")));
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
