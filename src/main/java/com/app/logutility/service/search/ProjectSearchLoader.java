package com.app.logutility.service.search;

import com.app.logutility.entity.project.FilterField;
import com.app.logutility.entity.project.LinePattern;
import com.app.logutility.entity.project.LogSource;
import com.app.logutility.entity.project.Project;
import com.app.logutility.repository.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Loads everything a search needs for a project inside a single read-only transaction and returns
 * it fully initialised, so the long-running (virtual-thread) file scan never touches a lazy
 * association or an open session. A separate bean so the transactional boundary is a real proxy
 * call, not a self-invocation from {@code SearchServiceImpl}.
 */
@Component
@RequiredArgsConstructor
public class ProjectSearchLoader {

    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public LoadedProject load(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        // Force-initialise the associations while the session is open; only scalar getters are
        // read afterwards, so the detached copies are safe to use across the async boundary.
        List<LogSource> nodes = List.copyOf(project.getLogSources());
        List<FilterField> fields = List.copyOf(project.getFilterFields());
        LinePattern linePattern = project.getLinePattern();
        if (linePattern != null) {
            linePattern.getTimestampPattern();
        }
        return new LoadedProject(nodes, fields, linePattern);
    }

    public record LoadedProject(List<LogSource> nodes, List<FilterField> fields, LinePattern linePattern) {
    }
}
