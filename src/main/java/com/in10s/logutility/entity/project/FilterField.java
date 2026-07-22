package com.in10s.logutility.entity.project;

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

import java.util.UUID;

/**
 * A searchable identifier field exposed on a project's search form (e.g. trace id,
 * session id). {@code linePrefix} captures how the value appears in a line when it is a
 * {@code key=value} token (e.g. {@code "tid="}); {@code mdcKey} records the originating
 * MDC key when the field was derived from a logback {@code %X{...}} pattern.
 */
@Entity
@Table(name = "filter_field")
@Getter
@Setter
public class FilterField {

    @Id
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // 'key' is a reserved word in SQL, so the column is named field_key.
    @Column(name = "field_key", nullable = false, length = 100)
    private String key;

    @Column(name = "label", nullable = false, length = 200)
    private String label;

    @Column(name = "mdc_key", length = 100)
    private String mdcKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 20)
    private MatchType matchType;

    @Column(name = "line_prefix", length = 100)
    private String linePrefix;
}
