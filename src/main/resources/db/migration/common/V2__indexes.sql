-- Secondary indexes for the foreign-key lookups used when loading a project's nodes and
-- filter fields. project.name and line_pattern.project_id are already uniquely indexed
-- via constraints created in V1.

CREATE INDEX idx_log_source_project_id ON log_source (project_id);
CREATE INDEX idx_filter_field_project_id ON filter_field (project_id);
