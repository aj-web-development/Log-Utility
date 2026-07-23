-- Dev-only convenience data: this file lives under the {vendor}=h2 Flyway location, so it only
-- ever runs against the in-memory H2 dev database (see application.yml's flyway.locations) and
-- never against a real PostgreSQL/prod target. Seeds one ready-to-search project so a fresh
-- `mvnw spring-boot:run` has something to search without going through the admin wizard first.

INSERT INTO project (id, name, description, created_at, updated_at)
VALUES ('3f6a1c2e-8b1d-4a2f-9c3e-1a2b3c4d5e6f', '360 API',
        'Dev seed project for the 360 API app, pointed at a local uniserve-web log directory.',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO log_source (id, project_id, node_label)
VALUES ('7d2e4f1a-9b3c-4d5e-8f6a-2b3c4d5e6f7a', '3f6a1c2e-8b1d-4a2f-9c3e-1a2b3c4d5e6f', 'Node1');

INSERT INTO log_file (id, log_source_id, file_label, live_log_path, backup_root_path,
                       backup_path_pattern, last_check_status)
VALUES ('5a1b2c3d-4e5f-4061-8a2b-3c4d5e6f7a8b', '7d2e4f1a-9b3c-4d5e-8f6a-2b3c4d5e6f7a', 'App Log',
        'E:\360-thin-client\360-api-workspaces\dev-360-1.5.0\360-api\logs\uniserve-web\uniserve-360-api.log',
        'E:\360-thin-client\360-api-workspaces\dev-360-1.5.0\360-api\logs\uniserve-web\uniserve-360-api-logs-backup',
        '{date}/uniserve-360-api.{HH}.{i}.log.gz', 'UNKNOWN');

INSERT INTO filter_field (id, project_id, field_key, label, match_type, line_prefix)
VALUES ('9c8b7a6f-5e4d-4c3b-8a2b-1a2b3c4d5e6f', '3f6a1c2e-8b1d-4a2f-9c3e-1a2b3c4d5e6f', 'tid',
        'Trace ID', 'EXACT_TOKEN', 'tid=');

-- Derived the same way the admin wizard's "analyze sample line" step would, from:
-- 2026-07-21 14:27:07.584 +0530 [http-nio-8081-exec-2] ERROR c.in10s.util.Utils :: tid=... :: - ...
INSERT INTO line_pattern (id, project_id, timestamp_pattern, timestamp_regex_or_position,
                           level_pattern, logger_pattern)
VALUES ('1e2d3c4b-5a6f-4e7d-8c9b-0a1b2c3d4e5f', '3f6a1c2e-8b1d-4a2f-9c3e-1a2b3c4d5e6f',
        'yyyy-MM-dd HH:mm:ss.SSS',
        '(\d{4}-\d{2}-\d{2})([ T])(\d{2}:\d{2}:\d{2})(?:([.,])(\d{1,9}))?',
        '\b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\b',
        '\b[a-zA-Z_$][\w$]*(?:\.[a-zA-Z_$][\w$]*)+\b');
