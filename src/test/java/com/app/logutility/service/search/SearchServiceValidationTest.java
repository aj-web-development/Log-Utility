package com.app.logutility.service.search;

import com.app.logutility.exception.search.SearchOverloadedException;
import com.app.logutility.request.project.NodeForm;
import com.app.logutility.request.project.ProjectWizardForm;
import com.app.logutility.service.project.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.app.logutility.request.search.SearchRequest;

/** Verifies the date-range limit and the server-wide concurrent-search cap. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = { "search.max-date-range-days=1", "search.max-concurrent-searches=1" })
class SearchServiceValidationTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private Semaphore searchConcurrencyGate;

    private UUID createProject() {
        ProjectWizardForm form = new ProjectWizardForm();
        form.setName("validation-test-" + UUID.randomUUID());
        NodeForm node = new NodeForm();
        node.setNodeLabel("node1");
        form.getNodes().add(node);
        return projectService.saveFromWizard(form);
    }

    @Test
    void rejectsADateRangeLargerThanTheConfiguredMaximum() {
        UUID projectId = createProject();
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(5); // max is 1 day

        assertThatThrownBy(() -> searchService.search(new SearchRequest(
                projectId, from, to, Map.of(), "", 0, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Date range too large");
    }

    @Test
    void rejectsAFromThatIsAfterTo() {
        UUID projectId = createProject();
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.plusHours(1);

        assertThatThrownBy(() -> searchService.search(new SearchRequest(
                projectId, from, to, Map.of(), "", 0, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be after");
    }

    @Test
    void rejectsASearchWhenTheConcurrencyCapIsAlreadySaturated() throws InterruptedException {
        UUID projectId = createProject();
        searchConcurrencyGate.acquire(); // the single permit (max-concurrent-searches=1) is now held
        try {
            assertThatThrownBy(() -> searchService.search(new SearchRequest(
                    projectId, LocalDateTime.now().minusHours(1), LocalDateTime.now(), Map.of(), "", 0, 0)))
                    .isInstanceOf(SearchOverloadedException.class);
        } finally {
            searchConcurrencyGate.release();
        }
    }
}
