-- Splits LogSource's single live/backup/pattern config into one or more labeled LogFile outputs
-- per node (a physical node can write app.log, error.log, access.log, ... in different places).

CREATE TABLE log_file (
    id                  VARCHAR(36)   NOT NULL,
    log_source_id       VARCHAR(36)   NOT NULL,
    file_label          VARCHAR(200),
    live_log_path       VARCHAR(1000),
    backup_root_path    VARCHAR(1000),
    backup_path_pattern VARCHAR(1000),
    last_checked_at     TIMESTAMP,
    last_check_status   VARCHAR(20)   NOT NULL DEFAULT 'UNKNOWN',
    last_check_message  VARCHAR(1000),
    CONSTRAINT pk_log_file PRIMARY KEY (id),
    CONSTRAINT fk_log_file_log_source FOREIGN KEY (log_source_id)
        REFERENCES log_source (id) ON DELETE CASCADE
);

CREATE INDEX idx_log_file_log_source_id ON log_file (log_source_id);

-- Carry forward each existing node's single config as its first output, reusing the log_source
-- row's own id as the new log_file id (a different table's PK space, so no collision) - this
-- avoids needing a vendor-specific UUID-generation function in a portable migration.
INSERT INTO log_file (id, log_source_id, file_label, live_log_path, backup_root_path,
                       backup_path_pattern, last_checked_at, last_check_status, last_check_message)
SELECT id, id, 'Application', live_log_path, backup_root_path, backup_path_pattern,
       last_checked_at, last_check_status, last_check_message
FROM log_source;

ALTER TABLE log_source DROP COLUMN live_log_path;
ALTER TABLE log_source DROP COLUMN backup_root_path;
ALTER TABLE log_source DROP COLUMN backup_path_pattern;
ALTER TABLE log_source DROP COLUMN last_checked_at;
ALTER TABLE log_source DROP COLUMN last_check_status;
ALTER TABLE log_source DROP COLUMN last_check_message;
