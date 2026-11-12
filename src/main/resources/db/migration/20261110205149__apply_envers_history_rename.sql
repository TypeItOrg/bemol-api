-- Apply the Envers history rename after the initial migration was already recorded.
-- Rename live relations only; no table data is copied or rewritten.
DO $$
DECLARE
    relation record;
BEGIN
    FOR relation IN
        SELECT tablename AS name
        FROM pg_tables
        WHERE schemaname = 'public' AND tablename LIKE 'envers_poc_%'
    LOOP
        EXECUTE format(
            'ALTER TABLE %I RENAME TO %I',
            relation.name,
            replace(relation.name, 'envers_poc_', 'envers_'));
    END LOOP;

    FOR relation IN
        SELECT conrelid::regclass AS table_name, conname AS name
        FROM pg_constraint
        WHERE connamespace = 'public'::regnamespace AND conname LIKE 'envers_poc_%'
    LOOP
        EXECUTE format(
            'ALTER TABLE %s RENAME CONSTRAINT %I TO %I',
            relation.table_name,
            relation.name,
            replace(relation.name, 'envers_poc_', 'envers_'));
    END LOOP;

    FOR relation IN
        SELECT indexname AS name
        FROM pg_indexes
        WHERE schemaname = 'public' AND indexname LIKE 'envers_poc_%'
    LOOP
        EXECUTE format(
            'ALTER INDEX %I RENAME TO %I',
            relation.name,
            replace(relation.name, 'envers_poc_', 'envers_'));
    END LOOP;

    FOR relation IN
        SELECT sequencename AS name
        FROM pg_sequences
        WHERE schemaname = 'public' AND sequencename LIKE 'envers_poc_%'
    LOOP
        EXECUTE format(
            'ALTER SEQUENCE %I RENAME TO %I',
            relation.name,
            replace(relation.name, 'envers_poc_', 'envers_'));
    END LOOP;
END $$;
