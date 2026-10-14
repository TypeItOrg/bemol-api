-- Fail before inserting business data if the dataset or prerequisites are incomplete.
DO $$
DECLARE
    settings record;
    missing text;
BEGIN
    SELECT * INTO STRICT settings FROM seed_settings;
    SELECT string_agg(m.role_code, ', ' ORDER BY m.role_code) INTO missing
    FROM (SELECT DISTINCT role_code FROM seed_members) m
    WHERE NOT EXISTS (SELECT 1 FROM roles r JOIN seed_context c USING (institution_id)
                      WHERE r.code = m.role_code AND r.is_system);
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Faltan roles de sistema para la institución: %. Inicializar la API primero.', missing;
    END IF;
    IF EXISTS (SELECT 1 FROM seed_instruments i JOIN seed_members m ON m.number = i.teacher_number
               WHERE i.offered AND m.role_code <> 'TEACHER')
       OR EXISTS (SELECT 1 FROM unnest(settings.group_teachers) AS teacher
                  WHERE NOT EXISTS (SELECT 1 FROM seed_members WHERE number = teacher AND role_code = 'TEACHER')) THEN
        RAISE EXCEPTION 'El dataset asigna clases a personas que no son docentes.';
    END IF;
    IF EXISTS (SELECT 1 FROM seed_programs WHERE levels < settings.offered_level
               OR individual_minutes > settings.block_minutes)
       OR extract(epoch FROM settings.day_start) + settings.block_minutes * settings.blocks_per_day * 60 >= 86400 THEN
        RAISE EXCEPTION 'Nivel o grilla horaria inválidos en el dataset.';
    END IF;
    IF EXISTS (SELECT 1 FROM unnest(settings.weekdays) AS day
               WHERE day <> ALL(ARRAY['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY']))
       OR cardinality(settings.weekdays) <> (SELECT count(DISTINCT day) FROM unnest(settings.weekdays) AS day) THEN
        RAISE EXCEPTION 'La grilla debe tener días válidos y sin repeticiones.';
    END IF;
    IF EXISTS (SELECT 1 FROM seed_curriculum c JOIN seed_programs p ON p.code = c.program
               WHERE c.level NOT BETWEEN 1 AND p.levels
                  OR (c.instrument_group <> 'none' AND NOT EXISTS (
                      SELECT 1 FROM seed_instrument_groups g JOIN seed_instruments i ON i.name = g.instrument
                      WHERE g.group_code = c.instrument_group AND i.offered))) THEN
        RAISE EXCEPTION 'El currículo contiene niveles o grupos instrumentales sin oferta válida.';
    END IF;
END $$;
