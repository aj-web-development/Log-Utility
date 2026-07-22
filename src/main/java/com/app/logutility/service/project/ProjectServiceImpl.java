package com.app.logutility.service.project;

import com.app.logutility.entity.project.CheckStatus;
import com.app.logutility.entity.project.FilterField;
import com.app.logutility.entity.project.LinePattern;
import com.app.logutility.entity.project.LogFile;
import com.app.logutility.entity.project.LogSource;
import com.app.logutility.exception.project.ProjectNotFoundException;
import com.app.logutility.repository.project.LogFileRepository;
import com.app.logutility.entity.project.MatchType;
import com.app.logutility.entity.project.Project;
import com.app.logutility.repository.project.ProjectRepository;
import com.app.logutility.service.validation.PathAvailabilityChecker;
import com.app.logutility.response.validation.PathCheckResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.app.logutility.request.project.FilterFieldForm;
import com.app.logutility.request.project.LinePatternForm;
import com.app.logutility.request.project.LogFileForm;
import com.app.logutility.request.project.NodeForm;
import com.app.logutility.request.project.ProjectWizardForm;
import com.app.logutility.response.project.PathCheckOutcome;
import com.app.logutility.response.project.ProjectSummaryDto;
import com.app.logutility.response.project.PublicFilterFieldView;
import com.app.logutility.response.project.PublicProjectView;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final LogFileRepository logFileRepository;
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
            for (LogFileForm lf : nf.getLogFiles()) {
                if (isLogFileEmpty(lf)) {
                    continue;
                }
                LogFile file = new LogFile();
                file.setFileLabel(trimToNull(lf.getFileLabel()));
                file.setLiveLogPath(trimToNull(lf.getLiveLogPath()));
                file.setBackupRootPath(trimToNull(lf.getBackupRootPath()));
                file.setBackupPathPattern(trimToNull(lf.getBackupPathPattern()));
                node.addLogFile(file);
            }
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
            nf.setLogSourceId(node.getId());
            for (LogFile file : node.getLogFiles()) {
                LogFileForm lf = new LogFileForm();
                lf.setFileLabel(file.getFileLabel());
                lf.setLiveLogPath(file.getLiveLogPath());
                lf.setBackupRootPath(file.getBackupRootPath());
                lf.setBackupPathPattern(file.getBackupPathPattern());
                lf.setLogFileId(file.getId());
                lf.setLastCheckStatus(file.getLastCheckStatus());
                lf.setLastCheckMessage(file.getLastCheckMessage());
                nf.getLogFiles().add(lf);
            }
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
    public void recordLogFileCheck(UUID logFileId, boolean reachable, String message) {
        logFileRepository.findById(logFileId).ifPresent(file -> {
            file.setLastCheckedAt(Instant.now());
            file.setLastCheckStatus(reachable ? CheckStatus.REACHABLE : CheckStatus.UNREACHABLE);
            file.setLastCheckMessage(message);
        });
    }

    @Override
    @Transactional
    public PathCheckOutcome checkPaths(String livePath, String backupPath, UUID logFileId) {
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

        if (logFileId != null) {
            recordLogFileCheck(logFileId, reachable, message);
        }
        return new PathCheckOutcome(reachable ? CheckStatus.REACHABLE : CheckStatus.UNREACHABLE, message);
    }

    private static boolean isNodeEmpty(NodeForm nf) {
        return !StringUtils.hasText(nf.getNodeLabel())
                && nf.getLogFiles().stream().allMatch(ProjectServiceImpl::isLogFileEmpty);
    }

    private static boolean isLogFileEmpty(LogFileForm lf) {
        return !StringUtils.hasText(lf.getFileLabel())
                && !StringUtils.hasText(lf.getLiveLogPath())
                && !StringUtils.hasText(lf.getBackupRootPath())
                && !StringUtils.hasText(lf.getBackupPathPattern());
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
