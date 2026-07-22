package com.app.logutility.service.project;

import com.app.logutility.entity.project.MatchType;
import com.app.logutility.repository.project.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import com.app.logutility.entity.project.LinePattern;
import com.app.logutility.request.project.FilterFieldForm;
import com.app.logutility.request.project.LinePatternForm;
import com.app.logutility.request.project.LogFileForm;
import com.app.logutility.request.project.NodeForm;
import com.app.logutility.request.project.ProjectWizardForm;
import com.app.logutility.response.project.ProjectSummaryDto;
import com.app.logutility.response.project.PublicProjectView;

/** Exercises project CRUD (create/update/delete/list, name uniqueness) against H2. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectServiceTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectRepository projectRepository;

    /** A form with one real node + field plus one empty trailing row of each (which must be skipped). */
    private ProjectWizardForm sampleForm(String name) {
        ProjectWizardForm form = new ProjectWizardForm();
        form.setName(name);
        form.setDescription("desc");

        NodeForm node = new NodeForm();
        node.setNodeLabel("node1");
        LogFileForm output = new LogFileForm();
        output.setLiveLogPath("/var/log/app.log");
        output.setBackupPathPattern("{date}/app.{HH}.{i}.log.gz");
        node.getLogFiles().add(output);
        node.getLogFiles().add(new LogFileForm()); // empty trailing output row -> skipped
        form.getNodes().add(node);
        form.getNodes().add(new NodeForm()); // empty trailing row -> skipped

        FilterFieldForm field = new FilterFieldForm();
        field.setKey("tid");
        field.setLabel("Trace ID");
        field.setMatchType(MatchType.EXACT_TOKEN);
        field.setLinePrefix("tid=");
        form.getFilterFields().add(field);
        form.getFilterFields().add(new FilterFieldForm()); // empty trailing row -> skipped
        return form;
    }

    @Test
    void createsProjectAndSkipsEmptyRows() {
        UUID id = projectService.saveFromWizard(sampleForm("svc-a"));

        List<ProjectSummaryDto> summaries = projectService.listProjects();
        ProjectSummaryDto summary = summaries.stream()
                .filter(s -> s.id().equals(id)).findFirst().orElseThrow();
        assertThat(summary.name()).isEqualTo("svc-a");
        assertThat(summary.nodeCount()).isEqualTo(1L);
        assertThat(summary.fieldCount()).isEqualTo(1L);
    }

    @Test
    void loadForEditRoundTrips() {
        UUID id = projectService.saveFromWizard(sampleForm("svc-b"));

        ProjectWizardForm form = projectService.loadForEdit(id);
        assertThat(form.getProjectId()).isEqualTo(id);
        assertThat(form.getName()).isEqualTo("svc-b");
        assertThat(form.getNodes()).hasSize(1);
        assertThat(form.getNodes().get(0).getNodeLabel()).isEqualTo("node1");
        assertThat(form.getFilterFields()).hasSize(1);
        assertThat(form.getFilterFields().get(0).getMatchType()).isEqualTo(MatchType.EXACT_TOKEN);
        assertThat(form.getFilterFields().get(0).getLinePrefix()).isEqualTo("tid=");
        assertThat(form.getNodes().get(0).getLogSourceId()).isNotNull();
        assertThat(form.getNodes().get(0).getLogFiles()).hasSize(1);
        assertThat(form.getNodes().get(0).getLogFiles().get(0).getLiveLogPath()).isEqualTo("/var/log/app.log");
        assertThat(form.getNodes().get(0).getLogFiles().get(0).getLogFileId()).isNotNull();
        assertThat(form.getNodes().get(0).getLogFiles().get(0).getLastCheckStatus())
                .isEqualTo(com.app.logutility.entity.project.CheckStatus.UNKNOWN);
    }

    @Test
    void nodeCanHaveMultipleLabeledOutputs() {
        ProjectWizardForm form = new ProjectWizardForm();
        form.setName("svc-multi-output");
        NodeForm node = new NodeForm();
        node.setNodeLabel("node1");
        LogFileForm app = new LogFileForm();
        app.setFileLabel("Application");
        app.setLiveLogPath("/var/log/app.log");
        LogFileForm error = new LogFileForm();
        error.setFileLabel("Error");
        error.setLiveLogPath("/var/log/error.log");
        node.getLogFiles().add(app);
        node.getLogFiles().add(error);
        form.getNodes().add(node);

        UUID id = projectService.saveFromWizard(form);

        NodeForm reloaded = projectService.loadForEdit(id).getNodes().get(0);
        assertThat(reloaded.getLogFiles()).hasSize(2);
        assertThat(reloaded.getLogFiles()).extracting(LogFileForm::getFileLabel)
                .containsExactlyInAnyOrder("Application", "Error");
        assertThat(reloaded.getLogFiles()).extracting(LogFileForm::getLiveLogPath)
                .containsExactlyInAnyOrder("/var/log/app.log", "/var/log/error.log");
        assertThat(reloaded.getLogFiles().stream().map(LogFileForm::getLogFileId))
                .doesNotContainNull().doesNotHaveDuplicates();
    }

    @Test
    void recordLogFileCheckUpdatesOutputForDisplay() {
        UUID projectId = projectService.saveFromWizard(sampleForm("svc-check"));
        UUID logFileId = projectService.loadForEdit(projectId).getNodes().get(0).getLogFiles().get(0).getLogFileId();

        projectService.recordLogFileCheck(logFileId, true, "Reachable — 3 entries");

        LogFileForm reloaded = projectService.loadForEdit(projectId).getNodes().get(0).getLogFiles().get(0);
        assertThat(reloaded.getLastCheckStatus()).isEqualTo(com.app.logutility.entity.project.CheckStatus.REACHABLE);
        assertThat(reloaded.getLastCheckMessage()).isEqualTo("Reachable — 3 entries");
    }

    @Test
    void recordLogFileCheckIsNoOpForUnknownId() {
        // Must never throw — a stale/removed output's check result is simply dropped.
        projectService.recordLogFileCheck(UUID.randomUUID(), false, "Path does not exist");
    }

    @Test
    void linePatternRoundTripsAndClearingRemovesIt() {
        ProjectWizardForm form = sampleForm("svc-linepattern");
        form.getLinePattern().setTimestampPattern("yyyy-MM-dd HH:mm:ss.SSS");
        form.getLinePattern().setLevelPattern("\\b(INFO|ERROR)\\b");
        form.getLinePattern().setLoggerPattern("com\\.acme\\..*");
        UUID id = projectService.saveFromWizard(form);

        ProjectWizardForm reloaded = projectService.loadForEdit(id);
        assertThat(reloaded.getLinePattern().getTimestampPattern()).isEqualTo("yyyy-MM-dd HH:mm:ss.SSS");
        assertThat(reloaded.getLinePattern().getLevelPattern()).isEqualTo("\\b(INFO|ERROR)\\b");
        assertThat(reloaded.getLinePattern().getLoggerPattern()).isEqualTo("com\\.acme\\..*");

        // Clearing every field on a subsequent save must remove the LinePattern row entirely.
        reloaded.setLinePattern(new LinePatternForm());
        projectService.saveFromWizard(reloaded);

        ProjectWizardForm clearedReload = projectService.loadForEdit(id);
        assertThat(clearedReload.getLinePattern().getTimestampPattern()).isNull();
        assertThat(clearedReload.getLinePattern().getLevelPattern()).isNull();
        assertThat(clearedReload.getLinePattern().getLoggerPattern()).isNull();
    }

    @Test
    void projectWithNoLinePatternLoadsWithEmptyForm() {
        UUID id = projectService.saveFromWizard(sampleForm("svc-no-linepattern"));

        ProjectWizardForm reloaded = projectService.loadForEdit(id);
        assertThat(reloaded.getLinePattern()).isNotNull();
        assertThat(reloaded.getLinePattern().hasAnyContent()).isFalse();
    }

    @Test
    void findPublicViewExposesOnlyKeyAndLabel() {
        UUID id = projectService.saveFromWizard(sampleForm("svc-public"));

        PublicProjectView view = projectService.findPublicView(id).orElseThrow();
        assertThat(view.name()).isEqualTo("svc-public");
        assertThat(view.fields()).hasSize(1);
        assertThat(view.fields().get(0).key()).isEqualTo("tid");
        assertThat(view.fields().get(0).label()).isEqualTo("Trace ID");
    }

    @Test
    void findPublicViewIsEmptyForUnknownId() {
        assertThat(projectService.findPublicView(UUID.randomUUID())).isEmpty();
    }

    @Test
    void updateReplacesCollections() {
        UUID id = projectService.saveFromWizard(sampleForm("svc-c"));

        ProjectWizardForm form = projectService.loadForEdit(id);
        form.setDescription("updated");
        form.getNodes().clear();
        NodeForm replacement = new NodeForm();
        replacement.setNodeLabel("node2");
        form.getNodes().add(replacement);
        projectService.saveFromWizard(form);

        ProjectWizardForm reloaded = projectService.loadForEdit(id);
        assertThat(reloaded.getDescription()).isEqualTo("updated");
        assertThat(reloaded.getNodes()).hasSize(1);
        assertThat(reloaded.getNodes().get(0).getNodeLabel()).isEqualTo("node2");
    }

    @Test
    void nameExistsRespectsExclusion() {
        UUID id = projectService.saveFromWizard(sampleForm("svc-d"));

        assertThat(projectService.nameExists("svc-d", null)).isTrue();
        assertThat(projectService.nameExists("svc-d", id)).isFalse(); // editing itself is fine
        assertThat(projectService.nameExists("does-not-exist", null)).isFalse();
    }

    @Test
    void deleteIsIdempotent() {
        UUID id = projectService.saveFromWizard(sampleForm("svc-e"));

        projectService.deleteProject(id);
        assertThat(projectRepository.findById(id)).isEmpty();
        projectService.deleteProject(id); // no error the second time
    }
}
