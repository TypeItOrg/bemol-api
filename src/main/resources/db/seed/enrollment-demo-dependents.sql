-- Connected family scenario, in the same transaction as the academic catalog.
CREATE TEMP TABLE seed_assets (prefix text NOT NULL, file_size bigint NOT NULL CHECK (file_size > 0)) ON COMMIT DROP;
INSERT INTO seed_assets VALUES (:'seed_asset_prefix', :'seed_asset_size'::bigint);

CREATE TEMP TABLE seed_dependent_courses ON COMMIT DROP AS
SELECT fixture.member_number, sp.display_order AS selection_order, c.*, cc.course_class_id, cd.day_of_week, cd.capacity,
       cs.course_class_schedule_id, cs.start_time, cs.end_time,
       s.format, s.name AS space_name, p.name AS plan_name, t.name AS path_name,
       l.name AS level_name, i.name AS instrument_name
FROM seed_dependents fixture JOIN courses c ON c.study_plan_id = pg_temp.academic_id('plan:' || fixture.program)
JOIN study_plan_spaces sp USING (study_plan_space_id)
JOIN academic_spaces s ON s.academic_space_id = sp.academic_space_id
JOIN study_plans p ON p.study_plan_id = sp.study_plan_id
JOIN training_paths t ON t.training_path_id = p.training_path_id
JOIN academic_levels l ON l.academic_level_id = sp.academic_level_id
LEFT JOIN instruments i USING (instrument_id)
JOIN course_classes cc USING (course_id)
JOIN course_class_days cd USING (course_class_id)
JOIN course_class_schedules cs USING (course_class_day_id)
WHERE c.institution_id = (SELECT institution_id FROM seed_context) AND c.status = 'ACTIVE'
  AND c.academic_year_id = (SELECT academic_year_id FROM academic_years
                           WHERE institution_id = (SELECT institution_id FROM seed_context) AND year = (SELECT academic_year FROM seed_context) AND deleted_at IS NULL)
  AND l.display_order = (SELECT offered_level FROM seed_settings)
  AND sp.display_order BETWEEN 1 AND 3 AND cc.class_number = 1
  AND (i.instrument_id IS NULL OR i.name = 'Piano');

DO $$
DECLARE
    tenant uuid := (SELECT institution_id FROM seed_context);
    cycle_year integer := (SELECT academic_year FROM seed_context);
    occurred timestamptz := (SELECT reference_date::timestamp AT TIME ZONE timezone FROM seed_context);
    fixture record;
    selected record;
    tutor uuid;
    reviewer uuid;
    dependent uuid;
    application uuid;
    document uuid := pg_temp.demo_id('document:musical-presentation');
    requirement uuid;
    selection uuid;
    enrollment uuid;
    slot uuid;
    starts time;
    ends time;
    inserted uuid;
    course_count integer;
BEGIN
    INSERT INTO document_definitions (id, institution_id, name, instructions, allowed_formats, active)
    VALUES (document, tenant, 'Presentación musical personal',
            'Contanos qué te interesa aprender y tu experiencia con la música.', '["application/pdf"]', true)
    ON CONFLICT (id) DO NOTHING;

    FOR fixture IN SELECT * FROM seed_dependents ORDER BY member_number LOOP
        tutor := pg_temp.demo_id('person:' || fixture.tutor_number);
        reviewer := pg_temp.demo_id('person:' || fixture.reviewer_number);
        dependent := pg_temp.demo_id('person:' || fixture.member_number);
        application := pg_temp.demo_id('application:dependent:' || fixture.member_number || ':' || cycle_year);
        requirement := pg_temp.demo_id('requirement:dependent:' || fixture.member_number || ':' || cycle_year);

        INSERT INTO person_guardians (person_guardian_id, institution_id, tutor_person_id, dependent_person_id,
                                      relationship, is_primary_contact, status, resolved_by_person_id, resolved_at,
                                      created_at, updated_at)
        VALUES (pg_temp.demo_id('guardian:' || fixture.tutor_number || ':' || fixture.member_number),
                tenant, tutor, dependent, 'MOTHER', true, 'ACTIVE', reviewer, occurred, occurred, occurred)
        ON CONFLICT (person_guardian_id) DO NOTHING;

        INSERT INTO training_path_document_requirements (id, institution_id, training_path_id, document_id,
                                                        level, display_order, active)
        VALUES (pg_temp.academic_id('document:musical-presentation:' || fixture.program), tenant,
                pg_temp.academic_id('path:' || fixture.program), document, 'OPTIONAL', 100, true)
        ON CONFLICT (id) DO NOTHING;

        -- Do not reopen, repopulate or reset an application edited through the demo UI.
        INSERT INTO enrollment_applications (enrollment_application_id, institution_id, applicant_person_id,
                                             submitted_by_person_id, training_path_id, study_plan_id,
                                             academic_year_id, enrollment_period_id, status, resolved_at,
                                             resolved_by_person_id, created_at, updated_at)
        SELECT application, tenant, dependent, tutor, pg_temp.academic_id('path:' || fixture.program),
               pg_temp.academic_id('plan:' || fixture.program), academic_year_id,
               pg_temp.academic_id('period:' || fixture.program || ':' || cycle_year), 'DRAFT', NULL, NULL, occurred, occurred
        FROM academic_years WHERE institution_id = tenant AND year = cycle_year AND deleted_at IS NULL
        ON CONFLICT (enrollment_application_id) DO NOTHING RETURNING enrollment_application_id INTO inserted;
        IF inserted IS NULL THEN
            CONTINUE;
        END IF;

        INSERT INTO applicant_responsibles (applicant_responsible_id, enrollment_application_id, full_name,
                                            document_number, phone_number, email, created_at, updated_at)
        SELECT pg_temp.demo_id('responsible:' || application), application, first_name || ' ' || last_name,
               document_number, '0000000000', email, occurred, occurred FROM people WHERE person_id = tutor;
        INSERT INTO applicant_education_backgrounds (applicant_education_background_id, enrollment_application_id,
                                                     currently_studying, education_level, school_origin,
                                                     secondary_completed, created_at, updated_at)
        VALUES (pg_temp.demo_id('education:' || application), application, fixture.member_number = 1,
                CASE WHEN fixture.member_number = 1 THEN 'INITIAL' ELSE 'SECONDARY' END,
                CASE WHEN fixture.member_number = 1 THEN 'Jardín Los Aromos' ELSE 'Escuela del Parque' END,
                fixture.member_number <> 1, occurred, occurred);
        INSERT INTO applicant_health_inclusions (applicant_health_inclusion_id, enrollment_application_id,
                                                 receives_reasonable_adjustments, created_at, updated_at)
        VALUES (pg_temp.demo_id('health:' || application), application, false, occurred, occurred);
        INSERT INTO applicant_preferences (applicant_preference_id, enrollment_application_id,
                                           preferred_shift, allows_image_use, is_reenrolling, created_at, updated_at)
        VALUES (pg_temp.demo_id('preference:' || application), application, 'Tarde', false, false, occurred, occurred);

        INSERT INTO enrollment_document_requirements (id, institution_id, application_id, source_requirement_id,
                                                     document_id, name, instructions, level, allowed_formats,
                                                     display_order, definition_revision, assignment_revision)
        SELECT requirement, tenant, application, r.id, d.id, d.name, d.instructions, r.level,
               d.allowed_formats, r.display_order, d.revision, r.revision
        FROM training_path_document_requirements r JOIN document_definitions d ON d.id = r.document_id
        WHERE r.id = pg_temp.academic_id('document:musical-presentation:' || fixture.program);
        IF EXISTS (SELECT 1 FROM training_path_document_requirements r
                   WHERE r.training_path_id = pg_temp.academic_id('path:' || fixture.program)
                     AND r.active AND r.level <> 'OPTIONAL' AND r.document_id <> document) THEN
            RAISE EXCEPTION 'El trayecto tiene requisitos documentales adicionales; revisarlos antes de cargar la demo.';
        END IF;
        INSERT INTO enrollment_attachments (enrollment_attachment_id, enrollment_application_id, requirement_id,
                                            original_file_name, storage_path, content_type, file_size,
                                            uploaded_by, uploader_type, review_status, reviewed_by,
                                            reviewer_type, reviewed_at, created_at, updated_at)
        SELECT pg_temp.demo_id('attachment:' || application), application, requirement,
               'presentacion-musical.pdf', prefix || '/application-' || fixture.member_number || '.pdf',
               'application/pdf', file_size, tutor, 'INSTITUTION',
               CASE WHEN fixture.application_status = 'APPROVED' THEN 'ACCEPTED' ELSE 'PENDING_REVIEW' END,
               CASE WHEN fixture.application_status = 'APPROVED' THEN reviewer END,
               CASE WHEN fixture.application_status = 'APPROVED' THEN 'INSTITUTION' END,
               CASE WHEN fixture.application_status = 'APPROVED' THEN occurred END, occurred, occurred
        FROM seed_assets;

        course_count := 0;
        FOR selected IN SELECT * FROM seed_dependent_courses
                        WHERE member_number = fixture.member_number ORDER BY selection_order
        LOOP
            course_count := course_count + 1;
            selection := pg_temp.demo_id('selection:' || application || ':' || selected.course_id);
            INSERT INTO enrollment_application_courses (enrollment_application_course_id, institution_id,
                    enrollment_application_id, enrollment_period_id, course_id, status, requested_at,
                    submitted_with_capacity, resolved_at, resolved_by_person_id, created_at, updated_at)
            VALUES (selection, tenant, application,
                    pg_temp.academic_id('period:' || fixture.program || ':' || cycle_year), selected.course_id,
                    'PENDING', occurred, true, NULL, NULL, occurred, occurred);
            INSERT INTO enrollment_application_spaces (enrollment_application_space_id, enrollment_application_id,
                                                       study_plan_space_id, instrument_id, created_at, updated_at)
            VALUES (pg_temp.demo_id('space-selection:' || selection), application, selected.study_plan_space_id,
                    selected.instrument_id, occurred, occurred);
        END LOOP;
        IF course_count <> 3 THEN
            RAISE EXCEPTION 'El escenario requiere exactamente tres cursos con comisión y horario; encontró %.', course_count;
        END IF;
        UPDATE enrollment_applications SET status = 'SUBMITTED' WHERE enrollment_application_id = application;
        INSERT INTO enrollment_admission_history (id, application_id, status, occurred_at, actor_id, account_type)
        VALUES (pg_temp.demo_id('admission:submitted:' || application), application, 'SUBMITTED', occurred, tutor, 'INSTITUTION');
        IF fixture.application_status = 'APPROVED' THEN
            INSERT INTO enrollment_admission_history (id, application_id, status, occurred_at, actor_id, account_type)
            VALUES (pg_temp.demo_id('admission:approved:' || application), application, 'APPROVED', occurred, reviewer, 'INSTITUTION');
        END IF;

        IF fixture.application_status <> 'APPROVED' THEN
            CONTINUE;
        END IF;
        UPDATE enrollment_applications SET status = 'APPROVED', resolved_at = occurred,
               resolved_by_person_id = reviewer WHERE enrollment_application_id = application;
        FOR selected IN SELECT * FROM seed_dependent_courses
                        WHERE member_number = fixture.member_number ORDER BY selection_order
        LOOP
            selection := pg_temp.demo_id('selection:' || application || ':' || selected.course_id);
            UPDATE enrollment_application_courses SET status = 'ENROLLED', resolved_at = occurred,
                   resolved_by_person_id = reviewer WHERE enrollment_application_course_id = selection;
            slot := NULL;
            starts := selected.start_time;
            ends := selected.end_time;
            IF selected.format = 'INDIVIDUAL' THEN
                SELECT s.course_individual_slot_id, s.start_time, s.end_time INTO STRICT slot, starts, ends
                FROM course_individual_slots s WHERE s.course_class_schedule_id = selected.course_class_schedule_id
                  AND NOT EXISTS (SELECT 1 FROM course_enrollment_schedules e
                                  WHERE e.course_individual_slot_id = s.course_individual_slot_id AND e.released_at IS NULL)
                ORDER BY s.start_time LIMIT 1;
            ELSIF (SELECT count(*) FROM course_enrollment_schedules e
                   WHERE e.course_class_schedule_id = selected.course_class_schedule_id AND e.released_at IS NULL) >= selected.capacity THEN
                RAISE EXCEPTION 'La comisión del escenario no tiene cupo; no se modifica la oferta.';
            END IF;
            enrollment := pg_temp.demo_id('enrollment:' || selection);
            INSERT INTO course_enrollments (course_enrollment_id, institution_id, student_id, course_id,
                    course_class_id, source, enrollment_application_id, enrollment_application_course_id,
                    status, academic_status, enrolled_at, action_authority_person_id, created_at, updated_at)
            VALUES (enrollment, tenant, pg_temp.demo_id('student:' || fixture.member_number), selected.course_id,
                    selected.course_class_id, 'APPLICATION', application, selection, 'ENROLLED', 'IN_PROGRESS',
                    occurred, reviewer, occurred, occurred);
            INSERT INTO course_enrollment_schedules (course_enrollment_schedule_id, institution_id,
                    course_enrollment_id, course_class_schedule_id, course_individual_slot_id,
                    day_of_week, start_time, end_time, created_at, updated_at)
            VALUES (pg_temp.demo_id('enrollment-schedule:' || enrollment), tenant, enrollment,
                    selected.course_class_schedule_id, slot, selected.day_of_week, starts, ends, occurred, occurred);
            INSERT INTO enrollment_application_course_assignment_snapshots (
                    enrollment_application_course_assignment_snapshot_id, institution_id,
                    enrollment_application_course_id, course_id, course_class_id, course_class_schedule_id,
                    course_individual_slot_id, course_name, training_path_name, study_plan_name,
                    academic_space_name, academic_level_name, instrument_name, day_of_week, start_time, end_time,
                    assigned_at, assigned_by_person_id, operation, created_at, updated_at)
            VALUES (pg_temp.demo_id('assignment:' || enrollment), tenant, selection, selected.course_id,
                    selected.course_class_id, selected.course_class_schedule_id, slot, selected.space_name,
                    selected.path_name, selected.plan_name, selected.space_name, selected.level_name,
                    selected.instrument_name, selected.day_of_week, starts, ends, occurred, reviewer,
                    'APPLICATION_ENROLLMENT', occurred, occurred);
            INSERT INTO course_enrollment_histories (course_enrollment_history_id, institution_id,
                    course_enrollment_id, new_status, new_academic_status, operation, authority_person_id,
                    changed_at, created_at, updated_at)
            VALUES (pg_temp.demo_id('enrollment-history:' || enrollment), tenant, enrollment,
                    'ENROLLED', 'IN_PROGRESS', 'APPLICATION_ENROLLMENT', reviewer, occurred, occurred, occurred);
        END LOOP;
    END LOOP;

    IF EXISTS (SELECT 1 FROM course_enrollment_schedules a JOIN course_enrollments ea USING (course_enrollment_id)
               JOIN course_enrollment_schedules b ON a.course_enrollment_schedule_id < b.course_enrollment_schedule_id
                    AND a.institution_id = b.institution_id AND a.day_of_week = b.day_of_week
                    AND a.start_time < b.end_time AND b.start_time < a.end_time
               JOIN course_enrollments eb ON eb.course_enrollment_id = b.course_enrollment_id
               WHERE ea.student_id = eb.student_id AND ea.institution_id = tenant
                 AND a.released_at IS NULL AND b.released_at IS NULL
                 AND ea.status = 'ENROLLED' AND eb.status = 'ENROLLED'
                 AND ea.student_id IN (SELECT pg_temp.demo_id('student:' || member_number) FROM seed_dependents)) THEN
        RAISE EXCEPTION 'El escenario produciría superposición de horarios; no se aplicaron cambios.';
    END IF;
END $$;
