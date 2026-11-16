-- Destructive: empties the whole database except migrations, permissions and platform accounts with their passkeys.
DO $$
DECLARE kept text[] := ARRAY['flyway_schema_history', 'permissions', 'platform_accounts', 'platform_passkey_credentials'];
BEGIN
    EXECUTE (SELECT 'TRUNCATE ' || string_agg(format('%I', tablename), ', ') || ' CASCADE'
             FROM pg_tables WHERE schemaname = 'public' AND tablename <> ALL(kept));
    IF (SELECT count(*) FROM platform_accounts) = 0 THEN
        RAISE EXCEPTION 'El reset vació las cuentas de plataforma; se revierte.';
    END IF;
END $$;
SELECT (SELECT count(*) FROM platform_accounts) AS cuentas_plataforma,
       (SELECT count(*) FROM platform_passkey_credentials) AS passkeys,
       (SELECT count(*) FROM institutions) AS instituciones,
       (SELECT count(*) FROM enrollment_applications) AS solicitudes;
