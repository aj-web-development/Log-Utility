package com.in10s.logutility.search;

import com.in10s.logutility.project.config.NodeForm;
import com.in10s.logutility.project.config.ProjectService;
import com.in10s.logutility.project.config.ProjectWizardForm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the hard result cap: scanning stops early and the result is flagged truncated. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "search.max-results=3")
class SearchServiceCapTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private ProjectService projectService;

    @TempDir
    Path tempDir;

    @Test
    void capsResultsAndSetsTruncated() throws IOException {
        Path liveLog = tempDir.resolve("app.log");
        LocalDateTime base = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0).withNano(0);
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            lines.add("%s INFO hit line number %d".formatted(
                    base.plusSeconds(i).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")), i));
        }
        Files.writeString(liveLog, String.join("\n", lines) + "\n");

        ProjectWizardForm form = new ProjectWizardForm();
        form.setName("cap-test-" + UUID.randomUUID());
        NodeForm node = new NodeForm();
        node.setNodeLabel("node1");
        node.setLiveLogPath(liveLog.toString());
        form.getNodes().add(node);
        UUID projectId = projectService.saveFromWizard(form);

        SearchResult result = searchService.search(new SearchRequest(
                projectId,
                base.minusHours(1),
                LocalDateTime.now().plusDays(1),
                Map.of(), "hit", 0, 0));

        assertThat(result.truncated()).isTrue();
        assertThat(result.lines()).hasSize(3);
        assertThat(result.totalMatched()).isEqualTo(3);
    }
}
