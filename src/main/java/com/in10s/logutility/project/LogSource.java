package com.in10s.logutility.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One node/server that holds a copy of a project's logs. Carries the live log-file path,
 * the rotated-backup root and its naming pattern (using {@code {date}/{HH}/{i}} placeholders),
 * plus the cached result of the last path-availability check.
 */
@Entity
@Table(name = "log_source")
@Getter
@Setter
public class LogSource {

    @Id
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "node_label", nullable = false, length = 200)
    private String nodeLabel;

    @Column(name = "live_log_path", length = 1000)
    private String liveLogPath;

    @Column(name = "backup_root_path", length = 1000)
    private String backupRootPath;

    @Column(name = "backup_path_pattern", length = 1000)
    private String backupPathPattern;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_check_status", nullable = false, length = 20)
    private CheckStatus lastCheckStatus = CheckStatus.UNKNOWN;

    @Column(name = "last_check_message", length = 1000)
    private String lastCheckMessage;
}
