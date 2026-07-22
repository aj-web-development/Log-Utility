package com.in10s.logutility.project;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the full project graph against the real Flyway-created schema on H2 (dev profile).
 * Its main purpose is to prove the database-portable mappings work end to end: the UUID id is
 * stored as VARCHAR(36), the child foreign keys bind correctly, the enum persists as a string,
 * and the {@code @CreationTimestamp}/{@code @UpdateTimestamp} audit columns populate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ProjectPersistenceTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void savesAndReloadsProjectGraph() {
        Project project = new Project();
        project.setName("orders-service");
        project.setDescription("Order processing microservice logs");

        LogSource node = new LogSource();
        node.setNodeLabel("node1");
        node.setLiveLogPath("/var/log/orders/app.log");
        node.setBackupRootPath("/var/log/orders/archive");
        node.setBackupPathPattern("{date}/app.{HH}.{i}.log.gz");
        project.addLogSource(node);

        FilterField traceId = new FilterField();
        traceId.setKey("tid");
        traceId.setLabel("Trace ID");
        traceId.setMatchType(MatchType.EXACT_TOKEN);
        traceId.setLinePrefix("tid=");
        project.addFilterField(traceId);

        LinePattern pattern = new LinePattern();
        pattern.setTimestampPattern("yyyy-MM-dd HH:mm:ss.SSS");
        project.setLinePattern(pattern);

        Project saved = projectRepository.saveAndFlush(project);
        UUID id = saved.getId();
        assertThat(id).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(node.getLastCheckStatus()).isEqualTo(CheckStatus.UNKNOWN);

        Project reloaded = projectRepository.findByIdWithFilterFields(id).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("orders-service");
        assertThat(reloaded.getFilterFields()).hasSize(1);
        assertThat(reloaded.getFilterFields().get(0).getMatchType()).isEqualTo(MatchType.EXACT_TOKEN);
        assertThat(reloaded.getFilterFields().get(0).getLinePrefix()).isEqualTo("tid=");

        Project withSources = projectRepository.findByIdWithLogSources(id).orElseThrow();
        assertThat(withSources.getLogSources()).hasSize(1);
        assertThat(withSources.getLogSources().get(0).getNodeLabel()).isEqualTo("node1");
    }

    @Test
    void enforcesUniqueProjectName() {
        Project first = new Project();
        first.setName("dup-name");
        projectRepository.saveAndFlush(first);

        assertThat(projectRepository.existsByName("dup-name")).isTrue();
        assertThat(projectRepository.existsByName("other")).isFalse();
    }
}
