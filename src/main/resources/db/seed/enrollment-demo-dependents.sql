-- Operational scenarios share the catalog transaction and preserve existing applications.
CREATE TEMP TABLE seed_assets (name text PRIMARY KEY, file_size bigint NOT NULL CHECK (file_size > 0)) ON COMMIT DROP;
INSERT INTO seed_assets VALUES
    ('dni-muestra.pdf', :'seed_dni_size'::bigint),
    ('dni-observado-muestra.pdf', :'seed_old_dni_size'::bigint),
    ('certificado-aptitud-fisica-muestra.pdf', :'seed_health_size'::bigint),
    ('partida-nacimiento-muestra.pdf', :'seed_guardian_size'::bigint),
    ('presentacion-musical.pdf', :'seed_music_size'::bigint);
CREATE TEMP TABLE seed_asset_context (prefix text NOT NULL) ON COMMIT DROP;
INSERT INTO seed_asset_context VALUES (:'seed_asset_prefix');

CREATE TEMP TABLE seed_scenario_courses ON COMMIT DROP AS
SELECT fixture.member_number, sp.display_order AS selection_order, c.*, cc.course_class_id, cd.day_of_week, cd.capacity,
       cs.course_class_schedule_id, cs.start_time, cs.end_time,
       s.format, s.name AS space_name, p.name AS plan_name, t.name AS path_name,
       l.name AS level_name, i.name AS instrument_name
FROM seed_applications fixture JOIN courses c ON c.study_plan_id = pg_temp.academic_id('plan:' || fixture.program)
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
                           WHERE institution_id = (SELECT institution_id FROM seed_context)
                             AND year = (SELECT academic_year FROM seed_context) AND deleted_at IS NULL)
  AND l.display_order = (SELECT offered_level FROM seed_settings)
  AND sp.display_order = ANY(fixture.positions) AND cc.class_number = 1
  AND (i.instrument_id IS NULL OR i.name = fixture.instrument);

DO $$
DECLARE
    tenant uuid := (SELECT institution_id FROM seed_context);
    cycle_year integer := (SELECT academic_year FROM seed_context);
    occurred timestamptz := (SELECT reference_date::timestamp AT TIME ZONE timezone FROM seed_context);
    fixture record;
    selected record;
    doc record;
    tutor uuid;
    reviewer uuid := pg_temp.demo_id('person:8');
    applicant uuid;
    application uuid;
    period_key uuid;
    requirement uuid;
    selection uuid;
    enrollment uuid;
    slot uuid;
    starts time;
    ends time;
    review text;
    rejection text := 'La solicitud fue rechazada por documentación ilegible; caso ficticio de demostración.';
    observation text := 'No se ve el dorso del DNI. Adjuntá una copia completa y legible.';
BEGIN
    FOR doc IN SELECT * FROM seed_documents ORDER BY position LOOP
        INSERT INTO document_definitions (id, institution_id, name, instructions, allowed_formats, active)
        VALUES (pg_temp.demo_id('document:' || doc.code), tenant, doc.name, doc.instructions,
                '["application/pdf","image/jpeg","image/png"]', true)
        ON CONFLICT (id) DO NOTHING;
        INSERT INTO training_path_document_requirements (id, institution_id, training_path_id, document_id,
                                                        level, display_order, active)
        SELECT pg_temp.academic_id('document:' || doc.code || ':' || program), tenant,
               pg_temp.academic_id('path:' || program), pg_temp.demo_id('document:' || doc.code),
               doc.level, doc.position, true
        FROM (SELECT DISTINCT program FROM seed_applications) paths
        ON CONFLICT (id) DO NOTHING;
    END LOOP;

    FOR fixture IN SELECT * FROM seed_guardians ORDER BY member_number LOOP
        tutor := pg_temp.demo_id('person:' || fixture.tutor_number);
        applicant := pg_temp.demo_id('person:' || fixture.member_number);
        INSERT INTO person_guardians (person_guardian_id, institution_id, tutor_person_id, dependent_person_id, declared_document_number, request_mode,
                                      relationship, is_primary_contact, status, resolved_by_person_id, resolved_at,
                                      created_at, updated_at)
        VALUES (pg_temp.demo_id('guardian:' || fixture.tutor_number || ':' || fixture.member_number),
                tenant, tutor, CASE WHEN fixture.status = 'ACTIVE' THEN applicant END,
                (SELECT document_number FROM people WHERE person_id = applicant), 'EXISTING', 'MOTHER', true, fixture.status,
                CASE WHEN fixture.status = 'ACTIVE' THEN pg_temp.demo_id('person:' || fixture.reviewer_number) END,
                CASE WHEN fixture.status = 'ACTIVE' THEN occurred END, occurred, occurred)
        ON CONFLICT (person_guardian_id) DO NOTHING;
        IF fixture.status = 'PENDING' THEN
            INSERT INTO person_guardian_attachments (person_guardian_attachment_id, institution_id, person_guardian_id,
                    original_file_name, storage_path, content_type, file_size, created_at, updated_at)
            SELECT pg_temp.demo_id('guardian-attachment:' || fixture.member_number), tenant,
                   pg_temp.demo_id('guardian:' || fixture.tutor_number || ':' || fixture.member_number),
                   a.name, c.prefix || '/' || a.name, 'application/pdf', a.file_size, occurred, occurred
            FROM seed_assets a CROSS JOIN seed_asset_context c WHERE a.name = 'partida-nacimiento-muestra.pdf'
            ON CONFLICT (person_guardian_attachment_id) DO NOTHING;
        END IF;
    END LOOP;

    -- Occupy both Piano slots before creating the waiting-list scenario.
    FOR fixture IN SELECT * FROM seed_applications ORDER BY (courses = 'WAITLISTED'), member_number LOOP
        tutor := pg_temp.demo_id('person:' || fixture.actor_number);
        applicant := pg_temp.demo_id('person:' || fixture.member_number);
        application := pg_temp.demo_id('application:' || fixture.scenario || ':' || cycle_year);
        SELECT pg_temp.academic_id('period:' || period_code || ':' || cycle_year) INTO STRICT period_key
        FROM seed_program_periods WHERE program = fixture.program;
        IF EXISTS (SELECT 1 FROM enrollment_applications WHERE enrollment_application_id = application) THEN
            CONTINUE;
        END IF;
        IF (SELECT count(*) FROM seed_scenario_courses WHERE member_number = fixture.member_number)
           <> cardinality(fixture.positions) THEN
            RAISE EXCEPTION 'El escenario % no tiene todos sus cursos activos con horario.', fixture.scenario;
        END IF;
        IF EXISTS (SELECT 1 FROM training_path_document_requirements r
                   WHERE r.training_path_id = pg_temp.academic_id('path:' || fixture.program)
                     AND r.active AND r.level <> 'OPTIONAL'
                     AND r.document_id NOT IN (SELECT pg_temp.demo_id('document:' || code) FROM seed_documents)) THEN
            RAISE EXCEPTION 'El trayecto tiene requisitos obligatorios ajenos al dataset; revisarlos antes de cargar la demo.';
        END IF;
        INSERT INTO enrollment_applications (enrollment_application_id, institution_id, applicant_person_id,
                submitted_by_person_id, training_path_id, academic_year_id, status, created_at, updated_at)
        SELECT application, tenant, applicant, CASE WHEN fixture.status <> 'DRAFT' AND fixture.status <> 'CANCELLED' THEN tutor END,
               pg_temp.academic_id('path:' || fixture.program), academic_year_id, 'DRAFT', occurred, occurred
        FROM academic_years WHERE institution_id = tenant AND year = cycle_year AND deleted_at IS NULL;
        INSERT INTO enrollment_admission_history (id, application_id, status, occurred_at, actor_id, account_type)
        VALUES (pg_temp.demo_id('admission:draft:' || application), application, 'DRAFT', occurred, tutor, 'INSTITUTION');

        IF fixture.actor_number <> fixture.member_number THEN
            INSERT INTO applicant_responsibles (applicant_responsible_id, enrollment_application_id, full_name,
                    document_number, phone_number, email, created_at, updated_at)
            SELECT pg_temp.demo_id('responsible:' || application), application, first_name || ' ' || last_name,
                   document_number, '03530000000', email, occurred, occurred FROM people WHERE person_id = tutor;
        END IF;
        INSERT INTO applicant_education_backgrounds (applicant_education_background_id, enrollment_application_id,
                currently_studying, education_level, school_origin, secondary_completed, created_at, updated_at)
        VALUES (pg_temp.demo_id('education:' || application), application, fixture.member_number IN (1,21),
                CASE WHEN fixture.member_number IN (1,21) THEN 'PRIMARY' ELSE 'SECONDARY' END,
                CASE WHEN fixture.member_number IN (1,21) THEN 'Escuela del Parque' END,
                fixture.member_number NOT IN (1,21), occurred, occurred);
        INSERT INTO applicant_health_inclusions (applicant_health_inclusion_id, enrollment_application_id,
                receives_reasonable_adjustments, created_at, updated_at)
        VALUES (pg_temp.demo_id('health:' || application), application, false, occurred, occurred);
        INSERT INTO applicant_preferences (applicant_preference_id, enrollment_application_id,
                preferred_shift, allows_image_use, is_reenrolling, created_at, updated_at)
        VALUES (pg_temp.demo_id('preference:' || application), application, 'Tarde', false, false, occurred, occurred);

        FOR doc IN SELECT * FROM seed_documents ORDER BY position LOOP
            requirement := pg_temp.demo_id('requirement:' || application || ':' || doc.code);
            INSERT INTO enrollment_document_requirements (id, institution_id, application_id, source_requirement_id,
                    document_id, name, instructions, level, allowed_formats, display_order, definition_revision, assignment_revision)
            SELECT requirement, tenant, application, r.id, d.id, d.name, d.instructions, r.level,
                   d.allowed_formats, r.display_order, d.revision, r.revision
            FROM training_path_document_requirements r JOIN document_definitions d ON d.id = r.document_id
            WHERE r.id = pg_temp.academic_id('document:' || doc.code || ':' || fixture.program);
            IF fixture.documents = 'MISSING'
               OR (fixture.documents = 'DEFERRED_MISSING' AND doc.level = 'BEFORE_CONFIRMATION') THEN
                CONTINUE;
            END IF;
            review := CASE
                WHEN fixture.documents = 'ACCEPTED' OR doc.level = 'BEFORE_CONFIRMATION'
                     OR fixture.documents = 'DEFERRED_MISSING' THEN 'ACCEPTED'
                WHEN fixture.documents = 'OBSERVED' AND doc.code = 'dni' THEN 'OBSERVED'
                ELSE 'PENDING_REVIEW' END;
            IF fixture.documents = 'REPLACED' AND doc.code = 'dni' THEN
                INSERT INTO enrollment_attachments (enrollment_attachment_id, enrollment_application_id, requirement_id,
                        original_file_name, storage_path, content_type, file_size, uploaded_by, uploader_type,
                        version_status, review_status, reviewed_by, reviewer_type, reviewed_at, observation, created_at, updated_at)
                SELECT pg_temp.demo_id('attachment:old:' || requirement), application, requirement, a.name,
                       c.prefix || '/' || a.name, 'application/pdf', a.file_size, tutor, 'INSTITUTION',
                       'SUPERSEDED', 'OBSERVED', reviewer, 'INSTITUTION', occurred + interval '1 minute',
                       observation, occurred, occurred + interval '2 minutes'
                FROM seed_assets a CROSS JOIN seed_asset_context c WHERE a.name = 'dni-observado-muestra.pdf';
            END IF;
            INSERT INTO enrollment_attachments (enrollment_attachment_id, enrollment_application_id, requirement_id,
                    original_file_name, storage_path, content_type, file_size, uploaded_by, uploader_type,
                    review_status, reviewed_by, reviewer_type, reviewed_at, observation, created_at, updated_at)
            SELECT pg_temp.demo_id('attachment:' || requirement), application, requirement, a.name,
                   c.prefix || '/' || a.name, 'application/pdf', a.file_size, tutor, 'INSTITUTION',
                   review, CASE WHEN review <> 'PENDING_REVIEW' THEN reviewer END,
                   CASE WHEN review <> 'PENDING_REVIEW' THEN 'INSTITUTION' END,
                   CASE WHEN review <> 'PENDING_REVIEW' THEN occurred + interval '3 minutes' END,
                   CASE WHEN review = 'OBSERVED' THEN observation END,
                   occurred + interval '2 minutes', occurred + interval '3 minutes'
            FROM seed_assets a CROSS JOIN seed_asset_context c WHERE a.name = doc.asset;
        END LOOP;

        FOR selected IN SELECT * FROM seed_scenario_courses WHERE member_number = fixture.member_number ORDER BY selection_order LOOP
            selection := pg_temp.demo_id('selection:' || application || ':' || selected.course_id);
            INSERT INTO enrollment_application_courses (enrollment_application_course_id, institution_id,
                    enrollment_application_id, enrollment_period_id, course_id, status, requested_at,
                    submitted_with_capacity, created_at, updated_at)
            VALUES (selection, tenant, application, period_key,
                    selected.course_id, 'PENDING',
                    CASE WHEN fixture.status NOT IN ('DRAFT','CANCELLED') THEN occurred + interval '5 minutes' END,
                    CASE WHEN fixture.status NOT IN ('DRAFT','CANCELLED') THEN fixture.courses <> 'WAITLISTED' END,
                    occurred, occurred);
        END LOOP;
        IF fixture.status NOT IN ('DRAFT','CANCELLED') THEN
            UPDATE enrollment_applications SET status = 'SUBMITTED' WHERE enrollment_application_id = application;
            INSERT INTO enrollment_admission_history (id, application_id, status, occurred_at, actor_id, account_type)
            VALUES (pg_temp.demo_id('admission:submitted:' || application), application, 'SUBMITTED',
                    occurred + interval '5 minutes', tutor, 'INSTITUTION');
        END IF;
        IF fixture.status NOT IN ('DRAFT','SUBMITTED') THEN
            UPDATE enrollment_applications SET status = fixture.status,
                   resolved_at = CASE WHEN fixture.status IN ('APPROVED','REJECTED') THEN occurred + interval '10 minutes' END,
                   resolved_by_person_id = CASE WHEN fixture.status IN ('APPROVED','REJECTED') THEN reviewer END,
                   rejection_reason = CASE WHEN fixture.status = 'REJECTED' THEN rejection END
            WHERE enrollment_application_id = application;
            INSERT INTO enrollment_admission_history (id, application_id, status, occurred_at, actor_id, account_type)
            VALUES (pg_temp.demo_id('admission:' || fixture.status || ':' || application), application, fixture.status,
                    occurred + interval '10 minutes', CASE WHEN fixture.status = 'CANCELLED' THEN tutor ELSE reviewer END, 'INSTITUTION');
        END IF;

        FOR selected IN SELECT * FROM seed_scenario_courses WHERE member_number = fixture.member_number ORDER BY selection_order LOOP
            selection := pg_temp.demo_id('selection:' || application || ':' || selected.course_id);
            IF fixture.courses IN ('REJECTED','CANCELLED') THEN
                UPDATE enrollment_application_courses SET status = fixture.courses,
                       resolved_at = occurred + interval '10 minutes', resolved_by_person_id = reviewer,
                       resolution_reason_code = CASE WHEN fixture.courses = 'REJECTED' THEN 'APPLICATION_REJECTED' END,
                       resolution_reason_text = CASE WHEN fixture.courses = 'REJECTED' THEN rejection END
                WHERE enrollment_application_course_id = selection;
                CONTINUE;
            END IF;
            IF fixture.courses = 'WAITLISTED' THEN
                IF EXISTS (SELECT 1 FROM course_individual_slots s
                           WHERE s.course_class_schedule_id = selected.course_class_schedule_id
                             AND NOT EXISTS (SELECT 1 FROM course_enrollment_schedules e
                                             WHERE e.course_individual_slot_id = s.course_individual_slot_id AND e.released_at IS NULL)) THEN
                    RAISE EXCEPTION 'El curso de lista de espera todavía tiene un turno libre; no se simula un cupo inexistente.';
                END IF;
                INSERT INTO course_waitlist_sequences (course_id, institution_id, last_number, created_at, updated_at)
                VALUES (selected.course_id, tenant, 1, occurred, occurred)
                ON CONFLICT (course_id) DO UPDATE SET last_number = course_waitlist_sequences.last_number + 1,
                    updated_at = occurred;
                UPDATE enrollment_application_courses SET status = 'WAITLISTED', waitlist_number =
                       (SELECT last_number FROM course_waitlist_sequences WHERE course_id = selected.course_id),
                       waitlisted_at = occurred + interval '10 minutes', waitlist_reason = 'NO_CAPACITY_AT_PARENT_APPROVAL'
                WHERE enrollment_application_course_id = selection;
                CONTINUE;
            END IF;
            IF fixture.courses <> 'ENROLLED' THEN
                CONTINUE;
            END IF;
            UPDATE enrollment_application_courses SET status = 'ENROLLED', resolved_at = occurred + interval '10 minutes',
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
                    occurred + interval '10 minutes', reviewer, occurred, occurred);
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
                    selected.instrument_name, selected.day_of_week, starts, ends,
                    occurred + interval '10 minutes', reviewer, 'APPLICATION_ENROLLMENT', occurred, occurred);
            INSERT INTO course_enrollment_histories (course_enrollment_history_id, institution_id,
                    course_enrollment_id, new_status, new_academic_status, operation, authority_person_id,
                    changed_at, created_at, updated_at)
            VALUES (pg_temp.demo_id('enrollment-history:' || enrollment), tenant, enrollment,
                    'ENROLLED', 'IN_PROGRESS', 'APPLICATION_ENROLLMENT', reviewer,
                    occurred + interval '10 minutes', occurred, occurred);
            IF selected.selection_order = fixture.completed_position THEN
                UPDATE course_enrollment_schedules SET released_at = occurred + interval '1 day'
                WHERE course_enrollment_id = enrollment;
                UPDATE course_enrollments SET status = 'COMPLETED', academic_status = 'PASSED',
                       completed_at = occurred + interval '1 day', action_reason = 'Resultado académico de muestra.'
                WHERE course_enrollment_id = enrollment;
                INSERT INTO course_enrollment_histories (course_enrollment_history_id, institution_id,
                        course_enrollment_id, previous_status, new_status, previous_academic_status,
                        new_academic_status, operation, reason, authority_person_id, changed_at, created_at, updated_at)
                VALUES (pg_temp.demo_id('result-history:' || enrollment), tenant, enrollment,
                        'ENROLLED', 'COMPLETED', 'IN_PROGRESS', 'PASSED', 'ACADEMIC_STATUS_UPDATE',
                        'Resultado académico de muestra.', reviewer, occurred + interval '1 day', occurred, occurred);
            END IF;
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
                 AND ea.student_id IN (SELECT pg_temp.demo_id('student:' || member_number) FROM seed_applications)) THEN
        RAISE EXCEPTION 'El escenario produciría superposición de horarios; no se aplicaron cambios.';
    END IF;
END $$;

-- Access fixtures are additive: reruns never reset a user's choices.
INSERT INTO person_guardians (person_guardian_id, institution_id, tutor_person_id, dependent_person_id,
                             declared_document_number, request_mode, relationship, status, resolved_at, created_at, updated_at)
SELECT pg_temp.demo_id('guardian:8:1'), institution_id, pg_temp.demo_id('person:8'), pg_temp.demo_id('person:1'),
       (SELECT document_number FROM people WHERE person_id = pg_temp.demo_id('person:1')), 'EXISTING', 'FATHER', 'ACTIVE', now(), now(), now() FROM seed_context
ON CONFLICT (person_guardian_id) DO NOTHING;
INSERT INTO person_guardians (person_guardian_id, institution_id, tutor_person_id, dependent_person_id,
                             declared_document_number, request_mode, relationship, status, resolved_at, created_at, updated_at)
SELECT pg_temp.demo_id('guardian:7:5'), institution_id, pg_temp.demo_id('person:7'), pg_temp.demo_id('person:5'),
       (SELECT document_number FROM people WHERE person_id = pg_temp.demo_id('person:5')), 'EXISTING', 'OTHER', 'ACTIVE', now(), now(), now() FROM seed_context
ON CONFLICT (person_guardian_id) DO NOTHING;
INSERT INTO person_guardians (person_guardian_id, institution_id, tutor_person_id, declared_document_number,
                             request_mode, declared_first_name, declared_last_name, declared_birth_date,
                             relationship, status, created_at, updated_at)
SELECT pg_temp.demo_id('guardian:pending:7:99000901'), institution_id, pg_temp.demo_id('person:7'), '44741326',
       'NEW', 'Persona', 'Pendiente', reference_date - interval '10 years', 'LEGAL_GUARDIAN', 'PENDING', now(), now() FROM seed_context
ON CONFLICT (person_guardian_id) DO NOTHING;
