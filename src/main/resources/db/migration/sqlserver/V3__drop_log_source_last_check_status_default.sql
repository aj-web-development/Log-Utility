-- SQL Server-only. V1__init.sql's "last_check_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN'"
-- makes SQL Server auto-create a DEFAULT constraint with a server-generated name (unlike
-- Postgres/MySQL/H2, which drop a column's default along with the column itself). SQL Server
-- refuses "ALTER TABLE ... DROP COLUMN last_check_status" while that constraint still
-- references it (error 4922), which is exactly what V4__log_file.sql does - so this must run
-- before V4, hence version 3 (previously an unused gap - see common/V4__log_file.sql's history
-- for why - but only in this sqlserver vendor location; common/ still has no V3).
--
-- Wrapped in BEGIN/END so Flyway's SQL Server parser executes it as one batch - a bare
-- DECLARE ... SELECT ... IF sequence split across separate JDBC calls would lose
-- @constraintName's value between them.
BEGIN
    DECLARE @constraintName NVARCHAR(200);

    SELECT @constraintName = dc.name
    FROM sys.default_constraints dc
    JOIN sys.columns c ON dc.parent_object_id = c.object_id AND dc.parent_column_id = c.column_id
    WHERE dc.parent_object_id = OBJECT_ID('log_source') AND c.name = 'last_check_status';

    IF @constraintName IS NOT NULL
        EXEC('ALTER TABLE log_source DROP CONSTRAINT ' + @constraintName);
END;
