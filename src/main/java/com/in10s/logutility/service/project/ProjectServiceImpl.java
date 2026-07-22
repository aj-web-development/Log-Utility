package com.in10s.logutility.service.project;

import com.in10s.logutility.entity.project.CheckStatus;
import com.in10s.logutility.entity.project.FilterField;
import com.in10s.logutility.entity.project.LinePattern;
import com.in10s.logutility.entity.project.LogSource;
import com.in10s.logutility.exception.project.ProjectNotFoundException;
import com.in10s.logutility.repository.project.LogSourceRepository;
import com.in10s.logutility.entity.project.MatchType;
import com.in10s.logutility.entity.project.Project;
import com.in10s.logutility.repository.project.ProjectRepository;
import com.in10s.logutility.service.validation.PathAvailabilityChecker;
import com.in10s.logutility.response.validation.PathCheckResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.in10s.logutility.request.project.FilterFieldForm;
import com.in10s.logutility.request.project.LinePatternForm;
import com.in10s.logutility.request.project.NodeForm;
import com.in10s.logutility.request.project.ProjectWizardForm;
import com.in10s.logutility.response.project.PathCheckOutcome;
import com.in10s.logutility.response.project.ProjectSummaryDto;
import com.in10s.logutility.response.project.PublicFilterFieldView;
import com.in10s.logutility.response.project.PublicProjectView;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final LogSourceRepository logSourceRepository;
    private final PathAvailabilityChecker pathAvailabilityChecker;

    @Override
    @Transactional(readOnly = true)
    public List<ProjectSummaryDto> listProjects() {
        return projectRepository.findAllSummaries();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublicProjectView> findPublicView(UUID id) {
        return projectRepository.findByIdWithFilterFields(id).map(project -> new PublicProjectView(
                project.getId(),
                project.getName(),
                project.getFilterFields().stream()
                        .map(f -> new PublicFilterFieldView(f.getKey(), f.getLabel()))
                        .toList()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean nameExists(String name, UUID excludingProjectId) {
        String trimmed = name == null ? "" : name.trim();
        if (excludingProjectId == null) {
            return projectRepository.existsByName(trimmed);
        }
        return projectRepository.existsByNameAndIdNot(trimmed, excludingProjectId);
    }

    @Override
    @Transactional
    public UUID saveFromWizard(ProjectWizardForm form) {
        Project project = form.getProjectId() == null
                ? new Project()
                : projectRepository.findById(form.getProjectId())
                        .orElseThrow(() -> new ProjectNotFoundException(form.getProjectId()));

        project.setName(trimToNull(form.getName()));
        project.setDescription(trimToNull(form.getDescription()));

        // Replace the node and field collections wholesale; orphanRemoval deletes the old rows.
        project.getLogSources().clear();
        for (NodeForm nf : form.getNodes()) {
            if (isNodeEmpty(nf)) {
                continue;
            }
            LogSource node = new LogSource();
            node.setNodeLabel(trimToNull(nf.getNodeLabel()));
            node.setLiveLogPath(trimToNull(nf.getLiveLogPath()));
            node.setBackupRootPath(trimToNull(nf.getBackupRootPath()));
            node.setBackupPathPattern(trimToNull(nf.getBackupPathPattern()));
            project.addLogSource(node);
        }

        project.getFilterFields().clear();
        for (FilterFieldForm ff : form.getFilterFields()) {
            if (!StringUtils.hasText(ff.getKey()) && !StringUtils.hasText(ff.getLabel())) {
                continue;
            }
            FilterField field = new FilterField();
            field.setKey(trimToNull(ff.getKey()));
            field.setLabel(trimToNull(ff.getLabel()));
            field.setMdcKey(trimToNull(ff.getMdcKey()));
            field.setMatchType(ff.getMatchType() == null ? MatchType.EXACT_TOKEN : ff.getMatchType());
            field.setLinePrefix(trimToNull(ff.getLinePrefix()));
            project.addFilterField(field);
        }

        LinePatternForm lpForm = form.getLinePattern();
        if (lpForm != null && lpForm.hasAnyContent()) {
            LinePattern linePattern = project.getLinePattern() != null ? project.getLinePattern() : new LinePattern();
            linePattern.setTimestampPattern(trimToNull(lpForm.getTimestampPattern()));
            linePattern.setTimestampRegexOrPosition(trimToNull(lpForm.getTimestampRegexOrPosition()));
            linePattern.setLevelPattern(trimToNull(lpForm.getLevelPattern()));
            linePattern.setLoggerPattern(trimToNull(lpForm.getLoggerPattern()));
            project.setLinePattern(linePattern);
        } else {
            // orphanRemoval on Project.linePattern deletes the previously-saved row, if any.
            project.setLinePattern(null);
        }

        return projectRepository.save(project).getId();
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectWizardForm loadForEdit(UUID id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));

        ProjectWizardForm form = new ProjectWizardForm();
        form.setProjectId(project.getId());
        form.setName(project.getName());
        form.setDescription(project.getDescription());

        for (LogSource node : project.getLogSources()) {
            NodeForm nf = new NodeForm();
            nf.setNodeLabel(node.getNodeLabel());
            nf.setLiveLogPath(node.getLiveLogPath());
            nf.setBackupRootPath(node.getBackupRootPath());
            nf.setBackupPathPattern(node.getBackupPathPattern());
            nf.setLogSourceId(node.getId());
            nf.setLastCheckStatus(node.getLastCheckStatus());
            nf.setLastCheckMessage(node.getLastCheckMessage());
            form.getNodes().add(nf);
        }
        for (FilterField field : project.getFilterFields()) {
            FilterFieldForm ff = new FilterFieldForm();
            ff.setKey(field.getKey());
            ff.setLabel(field.getLabel());
            ff.setMdcKey(field.getMdcKey());
            ff.setMatchType(field.getMatchType());
            ff.setLinePrefix(field.getLinePrefix());
            form.getFilterFields().add(ff);
        }

        LinePattern linePattern = project.getLinePattern();
        if (linePattern != null) {
            // sampleLine is a wizard-only scratch field, not persisted — only the derived
            // patterns come back; the admin can re-paste a line to refresh the preview.
            form.getLinePattern().setTimestampPattern(linePattern.getTimestampPattern());
            form.getLinePattern().setTimestampRegexOrPosition(linePattern.getTimestampRegexOrPosition());
            form.getLinePattern().setLevelPattern(linePattern.getLevelPattern());
            form.getLinePattern().setLoggerPattern(linePattern.getLoggerPattern());
        }
        return form;
    }

    @Override
    @Transactional
    public void deleteProject(UUID id) {
        projectRepository.findById(id).ifPresent(projectRepository::delete);
    }

    @Override
    @Transactional
    public void recordLogSourceCheck(UUID logSourceId, boolean reachable, String message) {
        logSourceRepository.findById(logSourceId).ifPresent(node -> {
            node.setLastCheckedAt(Instant.now());
            node.setLastCheckStatus(reachable ? CheckStatus.REACHABLE : CheckStatus.UNREACHABLE);
            node.setLastCheckMessage(message);
        });
    }

    @Override
    @Transactional
    public PathCheckOutcome checkPaths(String livePath, String backupPath, UUID logSourceId) {
        List<String> parts = new ArrayList<>();
        boolean anyConfigured = false;
        boolean allReachable = true;

        if (StringUtils.hasText(livePath)) {
            anyConfigured = true;
            PathCheckResult r = pathAvailabilityChecker.check(livePath);
            allReachable = allReachable && r.reachable();
            parts.add("Live: " + r.message());
        }
        if (StringUtils.hasText(backupPath)) {
            anyConfigured = true;
            PathCheckResult r = pathAvailabilityChecker.check(backupPath);
            allReachable = allReachable && r.reachable();
            parts.add("Backup: " + r.message());
        }

        boolean reachable = anyConfigured && allReachable;
        String message = anyConfigured ? String.join("; ", parts) : "No paths configured";

        if (logSourceId != null) {
            recordLogSourceCheck(logSourceId, reachable, message);
        }
        return new PathCheckOutcome(reachable ? CheckStatus.REACHABLE : CheckStatus.UNREACHABLE, message);
    }

    private static boolean isNodeEmpty(NodeForm nf) {
        return !StringUtils.hasText(nf.getNodeLabel())
                && !StringUtils.hasText(nf.getLiveLogPath())
                && !StringUtils.hasText(nf.getBackupRootPath())
                && !StringUtils.hasText(nf.getBackupPathPattern());
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
