package com.app.logutility.entity.project;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One node/server that holds a copy of a project's logs. A node can write more than one distinct
 * log output (app.log, error.log, access.log, ...); each is a separately configured
 * {@link LogFile} under this node.
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

    @OneToMany(mappedBy = "logSource", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LogFile> logFiles = new ArrayList<>();

    /** Adds a log output and keeps the inverse side of the association consistent. */
    public void addLogFile(LogFile file) {
        logFiles.add(file);
        file.setLogSource(this);
    }
}
