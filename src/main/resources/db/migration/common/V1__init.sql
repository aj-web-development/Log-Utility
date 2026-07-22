-- Core configuration schema for the Log Utility.
-- Portable across all mainstream SQL databases (PostgreSQL, MySQL/MariaDB, Oracle, H2, ...):
--   * UUID primary/foreign keys are stored as VARCHAR(36) rather than a native UUID type,
--     because UUID is not portable (MySQL/Oracle/SQL Server have no native UUID column).
--   * Enum values are stored as VARCHAR.
--   * TIMESTAMP is the SQL-standard datetime type. NOTE: on SQL Server TIMESTAMP is a
--     rowversion alias, so a SQL Server deployment needs an override script placed in
--     db/migration/sqlserver using DATETIME2 (see the {vendor} Flyway location).

CREATE TABLE project (
    id          VARCHAR(36)   NOT NULL,
    name        VARCHAR(200)  NOT NULL,
    description VARCHAR(2000),
    created_at  TIMESTAMP     NOT NULL,
    updated_at  TIMESTAMP     NOT NULL,
    CONSTRAINT pk_project PRIMARY KEY (id),
    CONSTRAINT uq_project_name UNIQUE (name)
);

CREATE TABLE log_source (
    id                  VARCHAR(36)   NOT NULL,
    project_id          VARCHAR(36)   NOT NULL,
    node_label          VARCHAR(200)  NOT NULL,
    live_log_path       VARCHAR(1000),
    backup_root_path    VARCHAR(1000),
    backup_path_pattern VARCHAR(1000),
    last_checked_at     TIMESTAMP,
    last_check_status   VARCHAR(20)   NOT NULL DEFAULT 'UNKNOWN',
    last_check_message  VARCHAR(1000),
    CONSTRAINT pk_log_source PRIMARY KEY (id),
    CONSTRAINT fk_log_source_project FOREIGN KEY (project_id)
        REFERENCES project (id) ON DELETE CASCADE
);

CREATE TABLE filter_field (
    id          VARCHAR(36)   NOT NULL,
    project_id  VARCHAR(36)   NOT NULL,
    field_key   VARCHAR(100)  NOT NULL,
    label       VARCHAR(200)  NOT NULL,
    mdc_key     VARCHAR(100),
    match_type  VARCHAR(20)   NOT NULL,
    line_prefix VARCHAR(100),
    CONSTRAINT pk_filter_field PRIMARY KEY (id),
    CONSTRAINT fk_filter_field_project FOREIGN KEY (project_id)
        REFERENCES project (id) ON DELETE CASCADE
);

CREATE TABLE line_pattern (
    id                          VARCHAR(36)   NOT NULL,
    project_id                  VARCHAR(36)   NOT NULL,
    timestamp_pattern           VARCHAR(200),
    timestamp_regex_or_position VARCHAR(500),
    level_pattern               VARCHAR(500),
    logger_pattern              VARCHAR(500),
    CONSTRAINT pk_line_pattern PRIMARY KEY (id),
    CONSTRAINT uq_line_pattern_project UNIQUE (project_id),
    CONSTRAINT fk_line_pattern_project FOREIGN KEY (project_id)
        REFERENCES project (id) ON DELETE CASCADE
);
