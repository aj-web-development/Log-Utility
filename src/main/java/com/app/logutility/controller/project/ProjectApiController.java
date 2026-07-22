package com.app.logutility.controller.project;

import com.app.logutility.entity.project.MatchType;
import com.app.logutility.exception.parser.LogbackParseException;
import com.app.logutility.request.parser.SampleLineRequest;
import com.app.logutility.request.project.FilterFieldRequest;
import com.app.logutility.request.project.LinePatternRequest;
import com.app.logutility.request.project.NodeRequest;
import com.app.logutility.request.project.PathCheckRequest;
import com.app.logutility.request.project.ProjectRequest;
import com.app.logutility.request.project.FilterFieldForm;
import com.app.logutility.request.project.LinePatternForm;
import com.app.logutility.request.project.NodeForm;
import com.app.logutility.request.project.ProjectWizardForm;
import com.app.logutility.response.parser.LogbackParseResult;
import com.app.logutility.response.parser.SampleLineAnalysis;
import com.app.logutility.response.project.FilterFieldResponse;
import com.app.logutility.response.project.LinePatternResponse;
import com.app.logutility.response.project.NodeResponse;
import com.app.logutility.response.project.PathCheckOutcome;
import com.app.logutility.response.project.ProjectDetailResponse;
import com.app.logutility.response.project.ProjectSummaryDto;
import com.app.logutility.service.parser.LogbackXmlParser;
import com.app.logutility.service.parser.SampleLineAnalyzer;
import com.app.logutility.service.project.ProjectService;
import com.app.logutility.service.project.ProjectWizardValidation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * REST equivalent of the admin project wizard/list ({@link ProjectAdminController} /
 * {@link ProjectWizardController}): full CRUD plus the parse/analyze/path-check helpers the
 * wizard uses while building a configuration. A create/update submits one complete payload rather
 * than the wizard's multi-step session draft — that draft is a UI convenience, not a business
 * rule — and both front doors share the same validation ({@link ProjectWizardValidation}) and
 * persistence ({@link ProjectService#saveFromWizard}).
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Admin (HTTP Basic): configure projects, nodes, and filter fields")
public class ProjectApiController {

    /**
     * Constrains {@code {id}} to a UUID's shape so it can never structurally collide with a
     * literal single-segment action route registered at this same path depth (e.g.
     * {@code /path-check}) — without this, {@code GET /api/projects/path-check} would silently
     * match here with {@code id="path-check"} instead of 405-ing on the real POST-only route.
     */
    private static final String UUID_PATTERN =
            "{id:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}";

    private final ProjectService projectService;
    private final LogbackXmlParser logbackXmlParser;
    private final SampleLineAnalyzer sampleLineAnalyzer;

    @GetMapping
    @Operation(summary = "List all projects", description = "Lightweight summaries, including node/field counts.")
    public List<ProjectSummaryDto> listProjects() {
        return projectService.listProjects();
    }

    @GetMapping("/" + UUID_PATTERN)
    @Operation(summary = "Get a project's full configuration", description = "Nodes, filter fields, and line pattern - the edit-prefill equivalent.")
    public ProjectDetailResponse getProject(@PathVariable UUID id) {
        return toDetailResponse(projectService.loadForEdit(id));
    }

    @PostMapping
    @Operation(summary = "Create a project", description = "One complete payload: name (required), nodes (at least one labeled), filter fields, and an optional line pattern.")
    public ResponseEntity<ProjectDetailResponse> createProject(@RequestBody ProjectRequest request) {
        ProjectWizardForm form = toForm(request, null);
        validate(form);
        UUID id = projectService.saveFromWizard(form);
        return ResponseEntity.created(URI.create("/api/projects/" + id)).body(toDetailResponse(projectService.loadForEdit(id)));
    }

    @PutMapping("/" + UUID_PATTERN)
    @Operation(summary = "Replace a project's configuration", description = "Same shape as create; nodes/fields are replaced wholesale, not merged.")
    public ProjectDetailResponse updateProject(@PathVariable UUID id, @RequestBody ProjectRequest request) {
        ProjectWizardForm form = toForm(request, id);
        validate(form);
        projectService.saveFromWizard(form);
        return toDetailResponse(projectService.loadForEdit(id));
    }

    @DeleteMapping("/" + UUID_PATTERN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a project", description = "Idempotent - deleting an already-missing id still returns 204.")
    public void deleteProject(@PathVariable UUID id) {
        projectService.deleteProject(id);
    }

    @PostMapping(value = "/logback/parse", consumes = "multipart/form-data")
    @Operation(summary = "Parse a logback-spring.xml", description = "Extracts MDC-field suggestions and the backup file pattern, to help pre-fill a create/update payload.")
    public LogbackParseResult parseLogback(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Choose a logback-spring.xml file to upload.");
        }
        String xmlContent;
        try {
            xmlContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LogbackParseException("Could not read the uploaded file.", e);
        }
        return logbackXmlParser.parse(xmlContent);
    }

    @PostMapping("/sample-line/analyze")
    @Operation(summary = "Analyze a sample log line", description = "Suggests timestamp/level/logger patterns to pre-fill a project's line pattern.")
    public SampleLineAnalysis analyzeSampleLine(@RequestBody SampleLineRequest request) {
        return sampleLineAnalyzer.analyze(request.sampleLine());
    }

    @PostMapping("/path-check")
    @Operation(summary = "Check a live/backup path pair", description = "logSourceId is optional - pass it to also record the result onto that persisted node.")
    public PathCheckOutcome checkPath(@RequestBody PathCheckRequest request) {
        return projectService.checkPaths(request.livePath(), request.backupPath(), request.logSourceId());
    }

    // ------------------------------------------------------------------ mapping helpers

    private void validate(ProjectWizardForm form) {
        String error = ProjectWizardValidation.validateDetails(form);
        if (error == null) {
            error = ProjectWizardValidation.validateNodes(form);
        }
        if (error == null) {
            error = ProjectWizardValidation.validateFields(form);
        }
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        if (projectService.nameExists(form.getName(), form.getProjectId())) {
            throw new IllegalStateException("A project named \"" + form.getName().trim() + "\" already exists.");
        }
    }

    private static ProjectWizardForm toForm(ProjectRequest request, UUID projectId) {
        ProjectWizardForm form = new ProjectWizardForm();
        form.setProjectId(projectId);
        form.setName(request.name());
        form.setDescription(request.description());

        for (NodeRequest nr : nonNull(request.nodes())) {
            NodeForm nf = new NodeForm();
            nf.setNodeLabel(nr.nodeLabel());
            nf.setLiveLogPath(nr.liveLogPath());
            nf.setBackupRootPath(nr.backupRootPath());
            nf.setBackupPathPattern(nr.backupPathPattern());
            form.getNodes().add(nf);
        }

        for (FilterFieldRequest fr : nonNull(request.filterFields())) {
            FilterFieldForm ff = new FilterFieldForm();
            ff.setKey(fr.key());
            ff.setLabel(fr.label());
            ff.setMdcKey(fr.mdcKey());
            ff.setMatchType(fr.matchType() == null ? MatchType.EXACT_TOKEN : fr.matchType());
            ff.setLinePrefix(fr.linePrefix());
            form.getFilterFields().add(ff);
        }

        LinePatternRequest lpr = request.linePattern();
        if (lpr != null) {
            LinePatternForm lpf = form.getLinePattern();
            lpf.setTimestampPattern(lpr.timestampPattern());
            lpf.setTimestampRegexOrPosition(lpr.timestampRegexOrPosition());
            lpf.setLevelPattern(lpr.levelPattern());
            lpf.setLoggerPattern(lpr.loggerPattern());
        }
        return form;
    }

    private static ProjectDetailResponse toDetailResponse(ProjectWizardForm form) {
        List<NodeResponse> nodes = form.getNodes().stream()
                .map(nf -> new NodeResponse(
                        nf.getLogSourceId(), nf.getNodeLabel(), nf.getLiveLogPath(),
                        nf.getBackupRootPath(), nf.getBackupPathPattern(),
                        nf.getLastCheckStatus(), nf.getLastCheckMessage()))
                .toList();

        List<FilterFieldResponse> fields = form.getFilterFields().stream()
                .map(ff -> new FilterFieldResponse(ff.getKey(), ff.getLabel(), ff.getMdcKey(), ff.getMatchType(), ff.getLinePrefix()))
                .toList();

        LinePatternForm lpf = form.getLinePattern();
        LinePatternResponse linePattern = lpf.hasAnyContent()
                ? new LinePatternResponse(lpf.getTimestampPattern(), lpf.getTimestampRegexOrPosition(),
                        lpf.getLevelPattern(), lpf.getLoggerPattern())
                : null;

        return new ProjectDetailResponse(form.getProjectId(), form.getName(), form.getDescription(), nodes, fields, linePattern);
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }
}
