-- Fixture infrastructure, outside Flyway and application startup.
-- enrollment-demo-context.sql owns the transaction and execution lock.
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

-- Normalize nested fixture IDs to stable logical tokens.
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

-- Shared namespaces keep logical identity stable across reruns.
CREATE FUNCTION pg_temp.demo_id(key text) RETURNS uuid LANGUAGE sql AS
$$ SELECT pg_temp.seed_id('bemol:enrollment-demo:v1:', key) $$;
CREATE FUNCTION pg_temp.academic_id(key text) RETURNS uuid LANGUAGE sql AS
$$ SELECT pg_temp.seed_id('bemol:enrollment:official-2025:', key) $$;
