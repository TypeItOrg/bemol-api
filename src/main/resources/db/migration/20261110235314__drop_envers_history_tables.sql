-- Remove the discarded Envers history while preserving the functional audit events.
DO $$
DECLARE
    history_table record;
BEGIN
    FOR history_table IN
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'public' AND tablename LIKE 'envers_%'
    LOOP
        EXECUTE format('DROP TABLE %I CASCADE', history_table.tablename);
    END LOOP;
END $$;

ALTER TABLE audit_events DROP COLUMN IF EXISTS transaction_id;
