-- psql variables are quoted as SQL literals here, never interpolated inside DO blocks.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout = '10s';
-- Keep the original lock also used by older versions of the seed/repair command.
SELECT pg_advisory_xact_lock(hashtext('boero:enrollment-demo:v1'));
CREATE TEMP TABLE seed_context (
    institution_id uuid NOT NULL, dataset text NOT NULL,
    academic_year integer NOT NULL CHECK (academic_year BETWEEN 1 AND 9998),
    reference_date date NOT NULL, timezone text NOT NULL,
    CHECK (extract(year FROM reference_date) = academic_year)
) ON COMMIT DROP;
INSERT INTO seed_context
SELECT institution_id, :'seed_dataset', :'seed_year'::integer,
       coalesce(nullif(:'seed_reference_date', '')::date, make_date(:'seed_year'::integer, 9, 1)),
       :'seed_timezone'
FROM institutions WHERE slug = :'seed_institution' AND active;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM seed_context) THEN
        RAISE EXCEPTION 'SEED_INSTITUTION debe ser el código de una institución existente y activa.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_timezone_names WHERE name = (SELECT timezone FROM seed_context)) THEN
        RAISE EXCEPTION 'SEED_TIMEZONE no es una zona horaria reconocida.';
    END IF;
END $$;
-- Serialize with domain workflows that lock the institution.
SELECT i.institution_id FROM institutions i JOIN seed_context c USING (institution_id) FOR UPDATE OF i;
SELECT institution_id, dataset, academic_year, reference_date, timezone FROM seed_context;
