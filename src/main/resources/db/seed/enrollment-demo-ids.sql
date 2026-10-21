-- Fixture infrastructure, outside Flyway and application startup.
-- enrollment-demo-context.sql owns the transaction and execution lock.
-- Never create a second registry beside the existing one: UUIDs belong to business rows.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'boero_demo') THEN
        IF EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'bemol_demo') THEN
            RAISE EXCEPTION 'Existen ambos registros de IDs; revisar antes de migrar para no duplicar datos.';
        END IF;
        ALTER SCHEMA boero_demo RENAME TO bemol_demo;
    END IF;
END $$;
CREATE SCHEMA IF NOT EXISTS bemol_demo;
-- Do not add foreign keys from fixture bookkeeping to application tables:
-- application lifecycle operations must not depend on seed-only infrastructure.
CREATE TABLE IF NOT EXISTS bemol_demo.scoped_identifiers (
    institution_id uuid NOT NULL,
    dataset text NOT NULL,
    namespace text NOT NULL,
    seed_key text NOT NULL,
    legacy_id uuid NOT NULL,
    id uuid NOT NULL UNIQUE DEFAULT uuidv7() CHECK (uuid_extract_version(id) = 7),
    PRIMARY KEY (institution_id, dataset, namespace, seed_key),
    UNIQUE (institution_id, dataset, legacy_id)
);

-- Preserve legacy_id as well as id: nested keys still resolve through the original UUID.
-- Conflicting old/new identities abort the enclosing transaction instead of merging data.
UPDATE bemol_demo.scoped_identifiers
SET dataset = CASE WHEN dataset = 'boero-2025' THEN 'bemol-2025' ELSE dataset END,
    namespace = CASE namespace
        WHEN 'boero:enrollment-demo:v1:' THEN 'bemol:enrollment-demo:v1:'
        WHEN 'boero:enrollment:official-2025:' THEN 'bemol:enrollment:official-2025:'
        ELSE namespace END
WHERE dataset = 'boero-2025'
   OR namespace IN ('boero:enrollment-demo:v1:', 'boero:enrollment:official-2025:');

-- Adopt the old global registry without changing a business UUID. Infer ownership
-- from actual rows, not an institution UUID/slug from another environment.
DO $$
DECLARE
    tenant uuid := (SELECT institution_id FROM seed_context);
    owners uuid[] := ARRAY[]::uuid[];
    table_owners uuid[];
    target record;
BEGIN
    IF to_regclass('bemol_demo.identifiers') IS NULL
       OR (SELECT dataset FROM seed_context) <> 'bemol-2025' THEN
        RETURN;
    END IF;
    FOR target IN
        SELECT c.conrelid::regclass AS table_name, a.attname AS id_column
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
        WHERE c.contype = 'p' AND cardinality(c.conkey) = 1 AND n.nspname = 'public'
          AND a.atttypid = 'uuid'::regtype
          AND EXISTS (SELECT 1 FROM pg_attribute x WHERE x.attrelid = c.conrelid
                      AND x.attname = 'institution_id' AND NOT x.attisdropped)
    LOOP
        EXECUTE format('SELECT array_agg(DISTINCT t.institution_id) FROM %s t
                        JOIN bemol_demo.identifiers i ON i.id = t.%I', target.table_name, target.id_column)
            INTO table_owners;
        owners := owners || coalesce(table_owners, ARRAY[]::uuid[]);
    END LOOP;
    IF tenant = ANY(owners) THEN
        IF EXISTS (SELECT 1 FROM unnest(owners) AS owner WHERE owner <> tenant) THEN
            RAISE EXCEPTION 'El registro histórico mezcla instituciones; revisar antes de adoptar sus IDs.';
        END IF;
        UPDATE bemol_demo.identifiers
        SET namespace = CASE namespace
            WHEN 'boero:enrollment-demo:v1:' THEN 'bemol:enrollment-demo:v1:'
            WHEN 'boero:enrollment:official-2025:' THEN 'bemol:enrollment:official-2025:'
            ELSE namespace END
        WHERE namespace IN ('boero:enrollment-demo:v1:', 'boero:enrollment:official-2025:');
        INSERT INTO bemol_demo.scoped_identifiers (institution_id, dataset, namespace, seed_key, legacy_id, id)
        SELECT tenant, 'bemol-2025', namespace, seed_key, legacy_id, id FROM bemol_demo.identifiers WHERE true
        ON CONFLICT (institution_id, dataset, namespace, seed_key) DO NOTHING;
    END IF;
END $$;

-- Normalize nested fixture IDs to stable logical tokens, including adopted IDs.
CREATE FUNCTION pg_temp.seed_key(key text) RETURNS text LANGUAGE plpgsql AS $$
DECLARE
    embedded text[];
    original uuid;
BEGIN
    FOR embedded IN SELECT regexp_matches(key, '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}', 'g') LOOP
        SELECT i.legacy_id INTO original FROM bemol_demo.scoped_identifiers i
        JOIN seed_context c USING (institution_id, dataset) WHERE i.id = embedded[1]::uuid;
        IF original IS NOT NULL THEN
            key := replace(key, embedded[1], original::text);
        END IF;
    END LOOP;
    RETURN key;
END $$;

CREATE FUNCTION pg_temp.seed_id(seed_namespace text, key text) RETURNS uuid LANGUAGE plpgsql AS $$
DECLARE
    canonical_key text := pg_temp.seed_key(key);
    result uuid;
BEGIN
    INSERT INTO bemol_demo.scoped_identifiers (institution_id, dataset, namespace, seed_key, legacy_id)
    SELECT institution_id, dataset, seed_namespace, canonical_key, md5(seed_namespace || canonical_key)::uuid
    FROM seed_context WHERE true
    ON CONFLICT (institution_id, dataset, namespace, seed_key) DO NOTHING;
    SELECT i.id INTO STRICT result FROM bemol_demo.scoped_identifiers i
    JOIN seed_context c USING (institution_id, dataset)
    WHERE i.namespace = seed_namespace AND i.seed_key = canonical_key;
    RETURN result;
END $$;

-- Registry migration preserves the logical identity and nested keys of earlier datasets.
CREATE FUNCTION pg_temp.demo_id(key text) RETURNS uuid LANGUAGE sql AS
$$ SELECT pg_temp.seed_id('bemol:enrollment-demo:v1:', key) $$;
CREATE FUNCTION pg_temp.academic_id(key text) RETURNS uuid LANGUAGE sql AS
$$ SELECT pg_temp.seed_id('bemol:enrollment:official-2025:', key) $$;
