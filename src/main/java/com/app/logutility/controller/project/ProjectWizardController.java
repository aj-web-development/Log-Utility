package com.app.logutility.controller.project;

import com.app.logutility.exception.parser.LogbackParseException;
import com.app.logutility.response.parser.LogbackParseResult;
import com.app.logutility.service.parser.LogbackXmlParser;
import com.app.logutility.response.parser.MdcFieldSuggestion;
import com.app.logutility.response.parser.SampleLineAnalysis;
import com.app.logutility.service.parser.SampleLineAnalyzer;
import com.app.logutility.entity.project.MatchType;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.app.logutility.request.project.FilterFieldForm;
import com.app.logutility.request.project.LinePatternForm;
import com.app.logutility.request.project.LogFileForm;
import com.app.logutility.request.project.NodeForm;
import com.app.logutility.request.project.ProjectWizardForm;
import com.app.logutility.response.project.PathCheckOutcome;
import com.app.logutility.response.project.WizardStep;
import com.app.logutility.service.project.ProjectService;
import com.app.logutility.service.project.ProjectWizardValidation;

/**
 * The HTMX-driven project setup wizard. The working {@link ProjectWizardForm} lives in the HTTP
 * session; every step POST binds that step's inputs into a fresh form, merges the relevant part
 * into the session draft, and returns the {@code stepBody} fragment for the next (or same, on
 * error) step so HTMX can swap it in place.
 *
 * <p>Because the draft is stored with Spring Session JDBC (which serialises attributes and only
 * persists those re-set via {@code setAttribute}), the {@link #step} helper re-sets the draft on
 * every response so in-place mutations are actually written back to the session store.
 */
@Controller
@RequestMapping("/admin/projects")
@RequiredArgsConstructor
public class ProjectWizardController {

    private static final String DRAFT_KEY = "projectWizardDraft";
    private static final String SHELL = "admin/projects/wizard";
    private static final String STEP_FRAGMENT = "admin/projects/steps :: stepBody";

    private final ProjectService projectService;
    private final LogbackXmlParser logbackXmlParser;
    private final SampleLineAnalyzer sampleLineAnalyzer;

    // ------------------------------------------------------------------ entry points

    @GetMapping("/new")
    public String startNew(HttpSession session, Model model) {
        ProjectWizardForm draft = new ProjectWizardForm();
        draft.getNodes().add(newNode());
        draft.getFilterFields().add(new FilterFieldForm());
        session.setAttribute(DRAFT_KEY, draft);
        populate(model, draft, WizardStep.DETAILS);
        return SHELL;
    }

    @GetMapping("/{id}/edit")
    public String startEdit(@PathVariable UUID id, HttpSession session, Model model) {
        ProjectWizardForm draft = projectService.loadForEdit(id);
        if (draft.getNodes().isEmpty()) {
            draft.getNodes().add(newNode());
        }
        for (NodeForm node : draft.getNodes()) {
            if (node.getLogFiles().isEmpty()) {
                node.getLogFiles().add(new LogFileForm());
            }
        }
        if (draft.getFilterFields().isEmpty()) {
            draft.getFilterFields().add(new FilterFieldForm());
        }
        session.setAttribute(DRAFT_KEY, draft);
        populate(model, draft, WizardStep.DETAILS);
        return SHELL;
    }

    @GetMapping("/upload")
    public String uploadForm() {
        return "admin/projects/upload";
    }

    /**
     * Parses an uploaded {@code logback-spring.xml} and seeds a fresh wizard draft from it: MDC
     * keys become {@code FilterField} rows and the rolling-file pattern becomes one node's
     * {@code backupPathPattern}. The name still must be entered by the admin (nothing in a
     * logback config names the project), and live/backup root paths are deliberately left blank
     * — see {@link ProjectWizardForm#uploadLiveLogHint}.
     */
    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file, HttpSession session, Model model) {
        if (file.isEmpty()) {
            model.addAttribute("error", "Choose a logback-spring.xml file to upload.");
            return "admin/projects/upload";
        }

        String xmlContent;
        try {
            xmlContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            model.addAttribute("error", "Could not read the uploaded file.");
            return "admin/projects/upload";
        }

        LogbackParseResult parsed;
        try {
            parsed = logbackXmlParser.parse(xmlContent);
        } catch (LogbackParseException e) {
            model.addAttribute("error", e.getMessage());
            return "admin/projects/upload";
        }

        ProjectWizardForm draft = new ProjectWizardForm();
        NodeForm node = new NodeForm();
        LogFileForm output = new LogFileForm();
        output.setBackupPathPattern(parsed.backupPathPattern());
        node.getLogFiles().add(output);
        draft.getNodes().add(node);
        draft.setUploadLiveLogHint(parsed.liveLogPathHint());
        draft.setUploadBackupRootHint(parsed.backupRootHint());

        for (MdcFieldSuggestion suggestion : parsed.mdcFields()) {
            FilterFieldForm field = new FilterFieldForm();
            field.setKey(suggestion.suggestedKey());
            field.setLabel(suggestion.suggestedLabel());
            field.setMdcKey(suggestion.mdcKey());
            field.setLinePrefix(suggestion.linePrefix());
            field.setMatchType(MatchType.EXACT_TOKEN);
            draft.getFilterFields().add(field);
        }
        if (draft.getFilterFields().isEmpty()) {
            draft.getFilterFields().add(new FilterFieldForm());
        }

        session.setAttribute(DRAFT_KEY, draft);
        populate(model, draft, WizardStep.DETAILS);
        return SHELL;
    }

    // ------------------------------------------------------------------ DETAILS step

    @PostMapping("/wizard/details")
    public String submitDetails(@ModelAttribute ProjectWizardForm submitted, HttpSession session, Model model) {
        ProjectWizardForm draft = draft(session);
        draft.setName(submitted.getName());
        draft.setDescription(submitted.getDescription());

        String error = ProjectWizardValidation.validateDetails(draft);
        if (error == null && projectService.nameExists(draft.getName(), draft.getProjectId())) {
            error = "A project named \"" + draft.getName().trim() + "\" already exists.";
        }
        return step(session, model, draft, error == null ? WizardStep.NODES : WizardStep.DETAILS, error);
    }

    // ------------------------------------------------------------------ NODES step

    @PostMapping("/wizard/nodes/add")
    public String addNode(@ModelAttribute ProjectWizardForm submitted, HttpSession session, Model model) {
        ProjectWizardForm draft = draft(session);
        draft.setNodes(nonNull(submitted.getNodes()));
        draft.getNodes().add(newNode());
        return step(session, model, draft, WizardStep.NODES, null);
    }

    @PostMapping("/wizard/nodes/remove")
    public String removeNode(@RequestParam int index, @ModelAttribute ProjectWizardForm submitted,
                             HttpSession session, Model model) {
        ProjectWizardForm draft = draft(session);
        draft.setNodes(nonNull(submitted.getNodes()));
        if (index >= 0 && index < draft.getNodes().size()) {
            draft.getNodes().remove(index);
        }
        if (draft.getNodes().isEmpty()) {
            draft.getNodes().add(newNode());
        }
        return step(session, model, draft, WizardStep.NODES, null);
    }

    @PostMapping("/wizard/nodes/{nodeIndex}/outputs/add")
    public String addLogFile(@PathVariable int nodeIndex, @ModelAttribute ProjectWizardForm submitted,
                             HttpSession session, Model model) {
        ProjectWizardForm draft = draft(session);
        draft.setNodes(nonNull(submitted.getNodes()));
        if (nodeIndex >= 0 && nodeIndex < draft.getNodes().size()) {
            draft.getNodes().get(nodeIndex).getLogFiles().add(new LogFileForm());
        }
        return step(session, model, draft, WizardStep.NODES, null);
    }

    @PostMapping("/wizard/nodes/{nodeIndex}/outputs/remove")
    public String removeLogFile(@PathVariable int nodeIndex, @RequestParam int index,
                                @ModelAttribute ProjectWizardForm submitted, HttpSession session, Model model) {
        ProjectWizardForm draft = draft(session);
        draft.setNodes(nonNull(submitted.getNodes()));
        if (nodeIndex >= 0 && nodeIndex < draft.getNodes().size()) {
            List<LogFileForm> outputs = draft.getNodes().get(nodeIndex).getLogFiles();
            if (index >= 0 && index < outputs.size()) {
                outputs.remove(index);
            }
            if (outputs.isEmpty()) {
                outputs.add(new LogFileForm());
            }
        }
        return step(session, model, draft, WizardStep.NODES, null);
    }

    /**
     * Backs each log-output row's single "Test paths" button. {@code LogFile} has one combined
     * check result (not one per path), so both the live and backup paths — whichever are
     * non-blank — are checked and folded into one status/message. Stateless with respect to the
     * wizard draft (it checks whatever the browser currently holds); if the row corresponds to an
     * already-persisted {@code LogFile} ({@code logFileId} present), the combined result is
     * also recorded on that row for later display. Never fails the request even when a path is
     * unreachable — it always renders a status badge.
     */
    @PostMapping("/wizard/nodes/test-path")
    public String testPath(@RequestParam(required = false) String livePath,
                           @RequestParam(required = false) String backupPath,
                           @RequestParam(required = false) String logFileId,
                           Model model) {
        // logFileId is only passed once the row backs an already-persisted LogFile; checkPaths
        // records the combined result onto it in that case (see ProjectService#recordLogFileCheck).
        UUID fileId = (logFileId != null && !logFileId.isBlank()) ? UUID.fromString(logFileId) : null;
        PathCheckOutcome outcome = projectService.checkPaths(livePath, backupPath, fileId);
        model.addAttribute("status", outcome.status().name());
        model.addAttribute("message", outcome.message());
        return "admin/projects/path-badge :: badge";
    }

    @PostMapping("/wizard/nodes")
    public String submitNodes(@ModelAttribute ProjectWizardForm submitted, HttpSession session, Model model) {
        ProjectWizardForm draft = draft(session);
        draft.setNodes(nonNull(submitted.getNodes()));
        String error = ProjectWizardValidation.validateNodes(draft);
        return step(session, model, draft, error == null ? WizardStep.SAMPLE_LINE : WizardStep.NODES, error);
    }

    @PostMapping("/wizard/nodes/back")
    public String nodesBack(@ModelAttribute ProjectWizardForm submitted, HttpSession session, Model model) {
        ProjectWizardForm draft = draft(session);
        draft.setNodes(nonNull(submitted.getNodes()));
        return step(session, model, draft, WizardStep.DETAILS, null);
    }

    // ------------------------------------------------------------------ SAMPLE_LINE step

    /**
     * Re-runs the {@link SampleLineAnalyzer} against whatever sample line the browser currently
     * holds and overwrites the suggested pattern fields with fresh guesses. Stays on the same
     * step so the admin can review the highlighted preview before confirming or editing further;
     * never fails — an unparseable or blank line just yields an "nothing detected" result.
     */
    @PostMapping("/wizard/sample-line/analyze")
    public String analyzeSampleLine(@ModelAttribute ProjectWizardForm submitted, HttpSession session, Model model) {
        ProjectWizardForm draft = draft(session);
        LinePatternForm submittedPattern = nonNullLinePattern(submitted.getLinePattern());
        String sampleLine = submittedPattern.getSampleLine();

        SampleLineAnalysis analysis = sampleLineAnalyzer.analyze(sampleLine);
        LinePatternForm linePattern = draft.getLinePattern();
        linePattern.setSampleLine(sampleLine);
        linePattern.setTimestampPattern(analysis.suggestedTimestampPattern());
        linePattern.setTimestampRegexOrPosition(analysis.suggestedTimestampRegex());
        linePattern.setLevelPattern(analysis.suggestedLevelPattern());
        linePattern.setLoggerPattern(analysis.suggestedLoggerPattern());

        return step(session, model, draft, WizardStep.SAMPLE_LINE, null);
    }

    @PostMapping("/wizard/sample-line")
    public String submitSampleLine(@ModelAttribute ProjectWizardForm submitted, HttpSession session, Model model) {
        ProjectWizardForm draft = draft(session);
        draft.setLinePattern(nonNullLinePattern(submitted.getLinePattern()));
        return step(session, model, draft, WizardStep.FIELDS, null);
    }

    @PostMapping("/wizard/sample-line/back")
    public String sampleLineBack(@ModelAttribute ProjectWizardForm submitted, HttpSession session, Model model) {
        ProjectWizardForm draft = draft(session);
        draft.setLinePattern(nonNullLinePattern(submitted.getLinePattern()));
        return step(session, model, draft, WizardStep.NODES, null);
    }

    // ------------------------------------------------------------------ FIELDS step

    @PostMapping("/wizard/fields/add")
    public String addField(@ModelAttribute ProjectWizardForm submitted, HttpSession session, Model model) {
        ProjectWizardForm draft = draft(session);
        draft.setFilterFields(nonNull(submitted.getFilterFields()));
        draft.getFilterFields().add(new FilterFieldForm());
        return step(session, model, draft, WizardStep.FIELDS, null);
    }

    @PostMapping("/wizard/fields/remove")
    public String removeField(@RequestParam int index, @ModelAttribute ProjectWizardForm submitted,
                              HttpSession session, Model model) {
        ProjectWizardForm draft = draft(session);
        draft.setFilterFields(nonNull(submitted.getFilterFields()));
        if (index >= 0 && index < draft.getFilterFields().size()) {
            draft.getFilterFields().remove(index);
        }
        return step(session, model, draft, WizardStep.FIELDS, null);
    }

    @PostMapping("/wizard/fields")
    public String submitFields(@ModelAttribute ProjectWizardForm submitted, HttpSession session, Model model) {
        ProjectWizardForm draft = draft(session);
        draft.setFilterFields(nonNull(submitted.getFilterFields()));
        String error = ProjectWizardValidation.validateFields(draft);
        return step(session, model, draft, error == null ? WizardStep.REVIEW : WizardStep.FIELDS, error);
    }

    @PostMapping("/wizard/fields/back")
    public String fieldsBack(@ModelAttribute ProjectWizardForm submitted, HttpSession session, Model model) {
        ProjectWizardForm draft = draft(session);
        draft.setFilterFields(nonNull(submitted.getFilterFields()));
        return step(session, model, draft, WizardStep.SAMPLE_LINE, null);
    }

    // ------------------------------------------------------------------ REVIEW / SAVE

    @PostMapping("/wizard/review/back")
    public String reviewBack(HttpSession session, Model model) {
        return step(session, model, draft(session), WizardStep.FIELDS, null);
    }

    @PostMapping("/wizard/save")
    public String save(HttpSession session, Model model, HttpServletResponse response) {
        ProjectWizardForm draft = draft(session);

        String error = ProjectWizardValidation.validateDetails(draft);
        if (error == null) {
            error = ProjectWizardValidation.validateNodes(draft);
        }
        if (error == null && projectService.nameExists(draft.getName(), draft.getProjectId())) {
            error = "A project named \"" + draft.getName().trim() + "\" already exists.";
        }
        if (error != null) {
            return step(session, model, draft, WizardStep.REVIEW, error);
        }

        projectService.saveFromWizard(draft);
        session.removeAttribute(DRAFT_KEY);
        // HTMX performs a client-side redirect on this header; the body is ignored.
        response.setHeader("HX-Redirect", "/admin/projects");
        return "admin/projects/steps :: saved";
    }

    // ------------------------------------------------------------------ helpers

    private ProjectWizardForm draft(HttpSession session) {
        ProjectWizardForm draft = (ProjectWizardForm) session.getAttribute(DRAFT_KEY);
        if (draft == null) {
            draft = new ProjectWizardForm();
            session.setAttribute(DRAFT_KEY, draft);
        }
        return draft;
    }

    private String step(HttpSession session, Model model, ProjectWizardForm draft, WizardStep step, String error) {
        // Re-set the (mutated) draft so Spring Session persists the change to the session store.
        session.setAttribute(DRAFT_KEY, draft);
        populate(model, draft, step);
        if (error != null) {
            model.addAttribute("error", error);
        }
        return STEP_FRAGMENT;
    }

    private void populate(Model model, ProjectWizardForm draft, WizardStep step) {
        model.addAttribute("form", draft);
        model.addAttribute("currentStep", step);
        model.addAttribute("steps", WizardStep.values());
        model.addAttribute("matchTypes", MatchType.values());
        model.addAttribute("analysis", sampleLineAnalyzer.analyze(draft.getLinePattern().getSampleLine()));
    }

    /** A blank node row always starts with one blank output row, mirroring the node-level guard. */
    private static NodeForm newNode() {
        NodeForm node = new NodeForm();
        node.getLogFiles().add(new LogFileForm());
        return node;
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }

    private static LinePatternForm nonNullLinePattern(LinePatternForm form) {
        return form == null ? new LinePatternForm() : form;
    }
}
