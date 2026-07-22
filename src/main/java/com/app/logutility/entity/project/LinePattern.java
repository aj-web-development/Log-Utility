package com.app.logutility.entity.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Describes how to parse a single log line for one project: the timestamp format plus how
 * to locate the timestamp, level, and logger tokens (each expressed as a regex or a
 * positional hint). One-to-one with {@link Project}.
 */
@Entity
@Table(name = "line_pattern")
@Getter
@Setter
public class LinePattern {

    @Id
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    /** {@link java.time.format.DateTimeFormatter} pattern, e.g. {@code yyyy-MM-dd HH:mm:ss.SSS Z}. */
    @Column(name = "timestamp_pattern", length = 200)
    private String timestampPattern;

    /** Regex (or positional hint) locating the timestamp substring within a line. */
    @Column(name = "timestamp_regex_or_position", length = 500)
    private String timestampRegexOrPosition;

    /** Regex (or positional hint) locating the level token. */
    @Column(name = "level_pattern", length = 500)
    private String levelPattern;

    /** Regex (or positional hint) locating the logger token. */
    @Column(name = "logger_pattern", length = 500)
    private String loggerPattern;
}
