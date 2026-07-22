package com.app.logutility.service.search;

import com.app.logutility.entity.project.MatchType;
import com.app.logutility.request.project.FilterFieldForm;
import com.app.logutility.request.project.LogFileForm;
import com.app.logutility.request.project.NodeForm;
import com.app.logutility.service.project.ProjectService;
import com.app.logutility.request.project.ProjectWizardForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import com.app.logutility.request.search.SearchRequest;
import com.app.logutility.response.search.LogLine;
import com.app.logutility.response.search.SearchResult;

/**
 * End-to-end search over a real project backed by real files on disk: a live plain log plus two
 * rotated .gz backups across two days, exercising fan-out, date pruning, the timestamp fast-reject,
 * field/free-text matching, timestamp-sorted merging, the result cap, and unreachable-node handling.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SearchServiceIntegrationTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private ProjectService projectService;

    @TempDir
    Path tempDir;

    private UUID projectId;
    private Path liveLog;
    private Path archiveDir;
    private LocalDateTime dayA;
    private LocalDateTime dayB;

    @BeforeEach
    void setUp() throws IOException {
        archiveDir = Files.createDirectories(tempDir.resolve("archive"));
        liveLog = tempDir.resolve("app.log");

        // Live file: today's lines (so the include-live decision picks it up).
        LocalDateTime today = LocalDateTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0);
        writePlain(liveLog, List.of(
                line(today, "INFO", "tid=live1 user request served"),
                line(today.plusMinutes(1), "ERROR", "tid=live2 payment failed"),
                "    at com.acme.Pay.charge(Pay.java:88)")); // continuation line, no timestamp

        // Rotated backups: two past days as .gz, named with the {date} the pruner expects. Kept
        // relative to "now" (not a fixed calendar date) so the fixture never drifts outside
        // search.max-date-range-days as real time passes.
        dayA = today.minusDays(2).withHour(9);
        writeGz(archiveDir.resolve("app.%s.0.log.gz".formatted(dayA.toLocalDate())), List.of(
                line(dayA, "INFO", "tid=old1 startup"),
                line(dayA.plusHours(1), "WARN", "tid=old2 retrying")));
        dayB = today.minusDays(1).withHour(9);
        writeGz(archiveDir.resolve("app.%s.0.log.gz".formatted(dayB.toLocalDate())), List.of(
                line(dayB, "INFO", "tid=old3 processed order")));

        ProjectWizardForm form = new ProjectWizardForm();
        form.setName("search-it-" + UUID.randomUUID());
        NodeForm node = new NodeForm();
        node.setNodeLabel("node1");
        LogFileForm output = new LogFileForm();
        output.setFileLabel("Application");
        output.setLiveLogPath(liveLog.toString());
        output.setBackupRootPath(archiveDir.toString());
        output.setBackupPathPattern("app.{date}.{i}.log.gz");
        node.getLogFiles().add(output);
        form.getNodes().add(node);
        FilterFieldForm tid = new FilterFieldForm();
        tid.setKey("tid");
        tid.setLabel("Trace ID");
        tid.setMatchType(MatchType.EXACT_TOKEN);
        tid.setLinePrefix("tid=");
        form.getFilterFields().add(tid);

        projectId = projectService.saveFromWizard(form);
    }

    @Test
    void searchesAcrossLiveAndRotatedFilesAndSortsByTimestamp() {
        SearchResult result = searchService.search(new SearchRequest(
                projectId,
                dayA.toLocalDate().atStartOfDay(),
                LocalDateTime.now().plusDays(1),
                Map.of(), "", 0, 0));

        // 5 entries (3 backup + 2 live); the stack-trace continuation line has no timestamp of its
        // own, so it is folded into the preceding tid=live2 entry rather than becoming a 6th one.
        assertThat(result.lines()).extracting(LogLine::raw)
                .anyMatch(l -> l.contains("tid=old1"))
                .anyMatch(l -> l.contains("tid=old3"))
                .anyMatch(l -> l.contains("tid=live2") && l.contains("Pay.charge"));
        assertThat(result.lines()).hasSize(5);
        assertThat(result.unreachableNodes()).isEmpty();
        assertThat(result.truncated()).isFalse();

        // Oldest backup line must sort before the newest live line.
        List<LocalDateTime> timestamps = result.lines().stream()
                .map(LogLine::timestamp).filter(t -> t != null).toList();
        assertThat(timestamps).isSorted();
    }

    @Test
    void datePruningExcludesOutOfRangeBackupDays() {
        // Range covers only dayB: the dayA backup file must be pruned entirely.
        SearchResult result = searchService.search(new SearchRequest(
                projectId,
                dayB.toLocalDate().atStartOfDay(),
                dayB.toLocalDate().atTime(23, 59),
                Map.of(), "", 0, 0));

        assertThat(result.lines()).extracting(LogLine::raw)
                .anyMatch(l -> l.contains("tid=old3"))
                .noneMatch(l -> l.contains("tid=old1"))
                .noneMatch(l -> l.contains("tid=old2"));
    }

    @Test
    void exactTokenFilterMatchesOneLine() {
        SearchResult result = searchService.search(new SearchRequest(
                projectId,
                dayA.toLocalDate().atStartOfDay(),
                LocalDateTime.now().plusDays(1),
                Map.of("tid", "old2"), "", 0, 0));

        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().get(0).raw()).contains("tid=old2 retrying");
        assertThat(result.lines().get(0).level()).isEqualTo("WARN");
    }

    @Test
    void freeTextFilterIsCaseInsensitive() {
        SearchResult result = searchService.search(new SearchRequest(
                projectId,
                dayA.toLocalDate().atStartOfDay(),
                LocalDateTime.now().plusDays(1),
                Map.of(), "PAYMENT", 0, 0));

        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().get(0).raw()).contains("payment failed");
    }

    @Test
    void searchesAcrossMultipleLogFilesUnderOneNode() throws IOException {
        // Add a second, independently configured output ("Error") to the existing node.
        Path errorLog = tempDir.resolve("error.log");
        LocalDateTime today = LocalDateTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0);
        writePlain(errorLog, List.of(line(today.plusMinutes(2), "ERROR", "tid=err1 disk full")));

        ProjectWizardForm form = projectService.loadForEdit(projectId);
        LogFileForm errorOutput = new LogFileForm();
        errorOutput.setFileLabel("Error");
        errorOutput.setLiveLogPath(errorLog.toString());
        form.getNodes().get(0).getLogFiles().add(errorOutput);
        projectService.saveFromWizard(form);

        SearchResult result = searchService.search(new SearchRequest(
                projectId,
                dayA.toLocalDate().atStartOfDay(),
                LocalDateTime.now().plusDays(1),
                Map.of(), "", 0, 0));

        assertThat(result.lines()).extracting(LogLine::fileLabel)
                .contains("Application", "Error");
        LogLine errLine = result.lines().stream()
                .filter(l -> l.raw().contains("tid=err1")).findFirst().orElseThrow();
        assertThat(errLine.nodeLabel()).isEqualTo("node1");
        assertThat(errLine.fileLabel()).isEqualTo("Error");
    }

    @Test
    void unreachableNodeIsReportedNotFatal() {
        // Add a second node pointing at nonexistent paths, then search.
        ProjectWizardForm form = projectService.loadForEdit(projectId);
        NodeForm ghost = new NodeForm();
        ghost.setNodeLabel("ghost");
        LogFileForm ghostOutput = new LogFileForm();
        ghostOutput.setLiveLogPath(tempDir.resolve("nope/app.log").toString());
        ghostOutput.setBackupRootPath(tempDir.resolve("nope-archive").toString());
        ghostOutput.setBackupPathPattern("app.{date}.{i}.log.gz");
        ghost.getLogFiles().add(ghostOutput);
        form.getNodes().add(ghost);
        projectService.saveFromWizard(form);

        SearchResult result = searchService.search(new SearchRequest(
                projectId,
                dayA.toLocalDate().atStartOfDay(),
                LocalDateTime.now().plusDays(1),
                Map.of("tid", "old1"), "", 0, 0));

        assertThat(result.unreachableNodes()).containsExactly("ghost");
        assertThat(result.lines()).hasSize(1); // node1 still returns its match
    }

    // ------------------------------------------------------------------ fixture helpers

    private static String line(LocalDateTime when, String level, String message) {
        return "%s [main] %-5s com.acme.App %s".formatted(
                when.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")),
                level, message);
    }

    private static void writePlain(Path file, List<String> lines) throws IOException {
        Files.writeString(file, String.join("\n", lines) + "\n");
    }

    private static void writeGz(Path file, List<String> lines) throws IOException {
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write((String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }
}
