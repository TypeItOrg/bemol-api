-- Enrollment dataset loader. Configuration and fixtures are supplied by run.sh.
-- Atomic, explicit execution only. No Flyway migration and no startup hook.
-- Run with the shared IDs and catalog scripts through make seed-demo.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM people WHERE institution_id = (SELECT institution_id FROM seed_context) AND person_id = md5('boero:enrollment-demo:v1:person:1')::uuid)
       OR EXISTS (SELECT 1 FROM training_paths p JOIN seed_programs s
                  ON p.training_path_id = md5('boero:enrollment:official-2025:path:' || s.code)::uuid
                  WHERE p.institution_id = (SELECT institution_id FROM seed_context))
       OR EXISTS (SELECT 1 FROM study_plans WHERE institution_id = (SELECT institution_id FROM seed_context) AND study_plan_id IN (
                  md5('boero:enrollment-demo:v1:plan:1')::uuid, md5('boero:enrollment-demo:v1:plan:2')::uuid)) THEN
        RAISE EXCEPTION 'Hay IDs del seed anterior. Se requiere revisar y migrar esos identificadores antes de cargar; no se modificaron datos.';
    END IF;
END $$;

DO $$
DECLARE
    tenant uuid := (SELECT institution_id FROM seed_context);
    cycle uuid;
    cycle_year integer := (SELECT academic_year FROM seed_context);
    role_key uuid;
    person_key uuid;
    member record;
    settings record;
BEGIN
    SELECT * INTO STRICT settings FROM seed_settings;

    FOR member IN SELECT * FROM seed_members ORDER BY number
    LOOP
        SELECT role_id INTO STRICT role_key FROM roles
        WHERE institution_id = tenant AND code = member.role_code AND is_system;
        person_key := pg_temp.demo_id('person:' || member.number);

        INSERT INTO people (person_id, institution_id, document_number, first_name, last_name,
                            email, birth_date, deleted, created_at, updated_at)
        VALUES (person_key, tenant, member.document, member.first_name, member.last_name,
                lower(translate(member.first_name || '.' || member.last_name, 'áéíóúñÁÉÍÓÚÑ', 'aeiounAEIOUN')) || '@' || settings.email_domain,
                ((SELECT reference_date FROM seed_context) - make_interval(years => member.age))::date, false, now(), now())
        ON CONFLICT (person_id) DO NOTHING;

        INSERT INTO users (user_id, institution_id, person_id, password, enabled,
                           email_verification_status, email_verified_at, created_at, updated_at)
        VALUES (pg_temp.demo_id('user:' || member.number), tenant, person_key, settings.password_hash,
                true, 'VERIFIED', now(), now(), now()) ON CONFLICT (user_id) DO NOTHING;

        INSERT INTO person_role_assignments (person_role_assignment_id, institution_id, person_id,
                                            role_id, access_scope, created_at, updated_at)
        VALUES (pg_temp.demo_id('role:' || member.number), tenant, person_key, role_key,
                'INSTITUTION', now(), now()) ON CONFLICT (person_role_assignment_id) DO NOTHING;

        IF member.role_code = 'STUDENT' THEN
            INSERT INTO students (student_id, institution_id, person_id, enrollment_date,
                                  status, file_number, created_at, updated_at)
            VALUES (pg_temp.demo_id('student:' || member.number), tenant, person_key,
                    (SELECT reference_date FROM seed_context), 'ACTIVE', cycle_year || '-' || member.student_file_suffix, now(), now())
            ON CONFLICT (student_id) DO NOTHING;
        END IF;
    END LOOP;

    SELECT academic_year_id INTO cycle FROM academic_years
    WHERE institution_id = tenant AND year = cycle_year AND deleted_at IS NULL;
    IF cycle IS NULL THEN
        cycle := pg_temp.demo_id('year:' || cycle_year);
        INSERT INTO academic_years (academic_year_id, institution_id, year, start_date, end_date,
                                    status, created_at, updated_at)
        VALUES (cycle, tenant, cycle_year, make_date(cycle_year, 1, 1), make_date(cycle_year, 12, 31),
                CASE WHEN EXISTS (SELECT 1 FROM academic_years WHERE institution_id = tenant AND status = 'ACTIVE')
                     THEN 'PLANNED' ELSE 'ACTIVE' END, now(), now());
    END IF;
    IF EXISTS (SELECT 1 FROM academic_years WHERE academic_year_id = cycle AND status = 'CLOSED') THEN
        RAISE EXCEPTION 'El ciclo actual está cerrado; preparar un ciclo vigente antes de cargar los datos.';
    END IF;

    INSERT INTO academic_years (academic_year_id, institution_id, year, start_date, end_date, status, created_at, updated_at)
    SELECT pg_temp.demo_id('year:' || (cycle_year + 1)), tenant, cycle_year + 1,
           make_date(cycle_year + 1, 1, 1), make_date(cycle_year + 1, 12, 31), 'PLANNED', now(), now()
    WHERE NOT EXISTS (SELECT 1 FROM academic_years WHERE institution_id = tenant AND year = cycle_year + 1 AND deleted_at IS NULL);

END $$;

DO $$
DECLARE
    tenant uuid := (SELECT institution_id FROM seed_context);
    cycle uuid;
    cycle_year integer := (SELECT academic_year FROM seed_context);
    program record;
    placement record;
    chosen_instrument record;
    catalog_name text;
    settings record;
    path_key uuid;
    plan_key uuid;
    level_key uuid;
    space_key uuid;
    curriculum_key uuid;
    period_key uuid;
    offering_key uuid;
    course_key uuid;
    class_key uuid;
    day_key uuid;
    schedule_key uuid;
    level_number integer;
    course_number integer := 0;
    period_minutes integer;
    slot_number integer;
    starts time;
    ends time;
    weekday text;
BEGIN
    SELECT * INTO STRICT settings FROM seed_settings;
    SELECT academic_year_id INTO STRICT cycle FROM academic_years
    WHERE institution_id = tenant AND year = cycle_year AND deleted_at IS NULL;

    FOR catalog_name IN SELECT name FROM seed_instruments ORDER BY name LOOP
        INSERT INTO instruments (instrument_id, institution_id, name, active, created_at, updated_at)
        SELECT pg_temp.academic_id('instrument:' || catalog_name), tenant, catalog_name, true, now(), now()
        WHERE NOT EXISTS (SELECT 1 FROM instruments WHERE institution_id = tenant AND deleted_at IS NULL
                          AND lower(name) = lower(catalog_name));
    END LOOP;
    IF EXISTS (SELECT 1 FROM seed_instruments d JOIN instruments i ON lower(i.name) = lower(d.name)
               WHERE i.institution_id = tenant AND i.deleted_at IS NULL AND NOT i.active) THEN
        RAISE EXCEPTION 'Un instrumento del dataset está inactivo; no se modifica ni se omite silenciosamente.';
    END IF;
    FOR catalog_name IN SELECT name FROM seed_shifts ORDER BY name LOOP
        INSERT INTO shifts (shift_id, institution_id, name, active, created_at, updated_at)
        SELECT pg_temp.academic_id('shift:' || catalog_name), tenant, catalog_name, true, now(), now()
        WHERE NOT EXISTS (SELECT 1 FROM shifts WHERE institution_id = tenant AND deleted_at IS NULL
                          AND lower(name) = lower(catalog_name));
    END LOOP;

    FOR program IN SELECT * FROM seed_programs ORDER BY code LOOP
        path_key := pg_temp.academic_id('path:' || program.code);
        plan_key := pg_temp.academic_id('plan:' || program.code);
        INSERT INTO training_paths (training_path_id, institution_id, name, description, active, created_at, updated_at)
        VALUES (path_key, tenant, program.name, NULL, true, now(), now()) ON CONFLICT (training_path_id) DO NOTHING;
        INSERT INTO study_plans (study_plan_id, institution_id, training_path_id, name, effective_from, status, created_at, updated_at)
        VALUES (plan_key, tenant, path_key, program.name || ' - ' || settings.plan_edition, settings.plan_effective_from, 'ACTIVE', now(), now())
        ON CONFLICT (study_plan_id) DO NOTHING;

        period_key := pg_temp.academic_id('period:' || program.code || ':' || cycle_year);
        offering_key := pg_temp.academic_id('offering:' || program.code || ':' || cycle_year);
        INSERT INTO enrollment_periods (enrollment_period_id, institution_id, academic_year_id, name,
                                        start_date, end_date, status, scope_configured, created_at, updated_at)
        VALUES (period_key, tenant, cycle, 'Inscripciones ' || cycle_year || ' - ' || program.name,
                make_date(cycle_year, 1, 1)::timestamp AT TIME ZONE (SELECT timezone FROM seed_context),
                (make_date(cycle_year + 1, 1, 1)::timestamp AT TIME ZONE (SELECT timezone FROM seed_context)) - interval '1 second',
                'OPEN', true, now(), now()) ON CONFLICT (enrollment_period_id) DO NOTHING;
        INSERT INTO enrollment_period_offerings (offering_id, enrollment_period_id, study_plan_id)
        VALUES (offering_key, period_key, plan_key) ON CONFLICT (offering_id) DO NOTHING;

        FOR level_number IN 1..program.levels LOOP
            level_key := pg_temp.academic_id('level:' || program.code || ':' || level_number);
            INSERT INTO academic_levels (academic_level_id, study_plan_id, name, display_order, description, created_at, updated_at)
            VALUES (level_key, plan_key, 'Nivel ' || level_number, level_number,
                    (SELECT description FROM seed_levels WHERE code = program.code AND level = level_number), now(), now())
            ON CONFLICT (academic_level_id) DO NOTHING;
            -- Only the configured level has operational classes; the full curriculum is retained.
            IF level_number = settings.offered_level THEN
                INSERT INTO enrollment_period_offering_levels (offering_level_id, offering_id, academic_level_id)
                VALUES (pg_temp.academic_id('offering-level:' || program.code || ':' || cycle_year), offering_key, level_key)
                ON CONFLICT (offering_level_id) DO NOTHING;
            END IF;
        END LOOP;

        FOR placement IN SELECT * FROM seed_curriculum WHERE seed_curriculum.program = program.code ORDER BY level, position LOOP
            level_key := pg_temp.academic_id('level:' || program.code || ':' || placement.level);
            SELECT academic_space_id INTO space_key FROM academic_spaces
            WHERE institution_id = tenant AND deleted_at IS NULL AND type = placement.type AND format = placement.format
              AND lower(translate(name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN')) =
                  lower(translate(placement.name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN'));
            IF space_key IS NULL THEN
                space_key := pg_temp.academic_id('space:' || placement.name || ':' || placement.type || ':' || placement.format);
                INSERT INTO academic_spaces (academic_space_id, institution_id, name, type, format, instrumental, active, created_at, updated_at)
                VALUES (space_key, tenant, placement.name, placement.type, placement.format,
                        placement.instrument_group <> 'none', true, now(), now());
            ELSIF EXISTS (SELECT 1 FROM academic_spaces WHERE academic_space_id = space_key
                          AND (instrumental <> (placement.instrument_group <> 'none') OR NOT active)) THEN
                RAISE EXCEPTION 'El espacio existente % tiene otra configuración; no se sobrescribe.', placement.name;
            END IF;
            curriculum_key := pg_temp.academic_id('curriculum:' || program.code || ':' || placement.level || ':' || placement.position);
            INSERT INTO study_plan_spaces (study_plan_space_id, institution_id, study_plan_id, academic_space_id,
                                           academic_level_id, requirement_type, display_order, approval_mode, created_at, updated_at)
            VALUES (curriculum_key, tenant, plan_key, space_key, level_key, 'REQUIRED', placement.position,
                    'PROMOTION_OR_FINAL_EXAM', now(), now()) ON CONFLICT (study_plan_space_id) DO NOTHING;

            IF placement.instrument_group <> 'none' THEN
                INSERT INTO study_plan_space_instruments (study_plan_space_instrument_id, institution_id,
                                                          study_plan_space_id, instrument_id, created_at, updated_at)
                SELECT pg_temp.academic_id('allowed:' || curriculum_key || ':' || i.instrument_id), tenant,
                       curriculum_key, i.instrument_id, now(), now()
                FROM instruments i WHERE i.institution_id = tenant AND i.deleted_at IS NULL AND i.active
                  AND EXISTS (SELECT 1 FROM seed_instrument_groups g
                              WHERE g.group_code = placement.instrument_group AND lower(g.instrument) = lower(i.name))
                ON CONFLICT (study_plan_space_instrument_id) DO NOTHING;
            END IF;

            IF placement.level <> settings.offered_level THEN
                CONTINUE;
            END IF;
            FOR chosen_instrument IN
                SELECT i.instrument_id, i.name, d.teacher_number FROM instruments i
                JOIN seed_instruments d ON lower(d.name) = lower(i.name) AND d.offered
                JOIN seed_instrument_groups g ON g.instrument = d.name AND g.group_code = placement.instrument_group
                WHERE i.institution_id = tenant AND i.deleted_at IS NULL AND i.active
                UNION ALL SELECT NULL::uuid, NULL::text, NULL::integer WHERE placement.instrument_group = 'none'
                ORDER BY name NULLS FIRST
            LOOP
                course_key := pg_temp.academic_id('course:' || curriculum_key || ':' || coalesce(chosen_instrument.instrument_id::text, 'group') || ':' || cycle_year);
                class_key := pg_temp.academic_id('class:' || course_key);
                day_key := pg_temp.academic_id('day:' || course_key);
                schedule_key := pg_temp.academic_id('schedule:' || course_key);
                -- Sequential afternoon/evening sample: no student or teacher overlaps across the fixture.
                -- This is not the institution's published weekly timetable or required course load.
                weekday := (settings.weekdays)[course_number / settings.blocks_per_day + 1];
                starts := settings.day_start + make_interval(mins => (course_number % settings.blocks_per_day) * settings.block_minutes);
                period_minutes := CASE WHEN placement.format <> 'INDIVIDUAL' THEN NULL
                                       ELSE program.individual_minutes END;
                ends := starts + make_interval(mins => CASE WHEN period_minutes IS NULL THEN settings.block_minutes
                    ELSE (settings.block_minutes / period_minutes) * period_minutes END);
                IF weekday IS NULL THEN
                    RAISE EXCEPTION 'La oferta excede los % horarios del dataset; ampliar su grilla.',
                        cardinality(settings.weekdays) * settings.blocks_per_day;
                END IF;
                course_number := course_number + 1;

                INSERT INTO courses (course_id, institution_id, study_plan_id, academic_space_id, academic_year_id,
                                     status, study_plan_space_id, training_path_id, academic_level_id, instrument_id, created_at, updated_at)
                VALUES (course_key, tenant, plan_key, space_key, cycle, 'ACTIVE', curriculum_key, path_key, level_key,
                        chosen_instrument.instrument_id, now(), now()) ON CONFLICT (course_id) DO NOTHING;
                INSERT INTO course_classes (course_class_id, institution_id, course_id, class_number, created_at, updated_at)
                VALUES (class_key, tenant, course_key, 1, now(), now()) ON CONFLICT (course_class_id) DO NOTHING;
                INSERT INTO course_class_teachers (course_class_teacher_id, institution_id, course_class_id, person_id, created_at, updated_at)
                VALUES (pg_temp.academic_id('teacher:' || course_key), tenant, class_key,
                        pg_temp.demo_id('person:' || coalesce(chosen_instrument.teacher_number,
                            settings.group_teachers[1 + course_number % cardinality(settings.group_teachers)])), now(), now())
                ON CONFLICT (course_class_teacher_id) DO NOTHING;
                INSERT INTO course_class_days (course_class_day_id, institution_id, course_class_id, day_of_week,
                                               capacity, period_duration_minutes, created_at, updated_at)
                VALUES (day_key, tenant, class_key, weekday, CASE WHEN placement.format = 'GRUPAL' THEN settings.group_capacity ELSE NULL END,
                        period_minutes, now(), now()) ON CONFLICT (course_class_day_id) DO NOTHING;
                INSERT INTO course_class_schedules (course_class_schedule_id, institution_id, course_class_day_id,
                                                   start_time, end_time, created_at, updated_at)
                VALUES (schedule_key, tenant, day_key, starts, ends, now(), now()) ON CONFLICT (course_class_schedule_id) DO NOTHING;
                IF period_minutes IS NOT NULL THEN
                    FOR slot_number IN 0..(settings.block_minutes / period_minutes - 1) LOOP
                        INSERT INTO course_individual_slots (course_individual_slot_id, institution_id, course_class_schedule_id,
                                                             start_time, end_time, created_at, updated_at)
                        VALUES (pg_temp.academic_id('slot:' || course_key || ':' || slot_number), tenant, schedule_key,
                                starts + slot_number * make_interval(mins => period_minutes),
                                starts + (slot_number + 1) * make_interval(mins => period_minutes), now(), now())
                        ON CONFLICT (course_individual_slot_id) DO NOTHING;
                    END LOOP;
                END IF;
            END LOOP;
        END LOOP;
    END LOOP;
END $$;
COMMIT;
\echo 'Carga completada. Los registros existentes no se sobrescribieron.'
