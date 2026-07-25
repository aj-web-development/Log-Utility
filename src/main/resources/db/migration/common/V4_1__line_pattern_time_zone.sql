-- The zone a project's raw log timestamp digits are written in (see DefaultLogLineParser),
-- used to convert a search's absolute from/to instant to that project's wall-clock and back.
-- Defaults every existing project to UTC, preserving current (post-fix) search behavior.
-- No "COLUMN" keyword: SQL Server's ALTER TABLE ADD doesn't accept it at all (unlike
-- Postgres/MySQL/H2, where it's optional) - omitting it is portable across all four.
ALTER TABLE line_pattern ADD time_zone VARCHAR(64) NOT NULL DEFAULT 'UTC';
