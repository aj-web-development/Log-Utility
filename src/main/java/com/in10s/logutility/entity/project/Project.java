package com.in10s.logutility.entity.project;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A configured target application whose logs can be searched. Aggregates the nodes
 * ({@link LogSource}), the searchable {@link FilterField}s, and the {@link LinePattern}
 * describing how to parse a log line.
 */
@Entity
@Table(name = "project")
@Getter
@Setter
public class Project {

    @Id
    @UuidGenerator
    // Store the UUID as a 36-char string so the schema is portable to databases without a
    // native UUID type (MySQL, Oracle, SQL Server). See db/migration/common/V1__init.sql.
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 200)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LogSource> logSources = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<FilterField> filterFields = new ArrayList<>();

    @OneToOne(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private LinePattern linePattern;

    /** Adds a node and keeps the inverse side of the association consistent. */
    public void addLogSource(LogSource source) {
        logSources.add(source);
        source.setProject(this);
    }

    /** Adds a filter field and keeps the inverse side of the association consistent. */
    public void addFilterField(FilterField field) {
        filterFields.add(field);
        field.setProject(this);
    }

    /** Sets the one-to-one line pattern, wiring the owning side's back-reference. */
    public void setLinePattern(LinePattern linePattern) {
        this.linePattern = linePattern;
        if (linePattern != null) {
            linePattern.setProject(this);
        }
    }
}
