DO $$
#variable_conflict use_variable
DECLARE
    institution_id UUID := '019e18e4-d919-76d8-9848-7f1b14e64452';
    academic_year_id UUID := '019e18e4-0000-7000-8000-000000000001';
    period_id UUID := '019e18e4-0000-7000-8000-000000000002';
    training_path_music_id UUID := '019e18e4-0000-7000-8000-000000000010';
    training_path_production_id UUID := '019e18e4-0000-7000-8000-000000000011';
    study_plan_music_id UUID := '019e18e4-0000-7000-8000-000000000020';
    study_plan_production_id UUID := '019e18e4-0000-7000-8000-000000000021';
    level_initial_id UUID := '019e18e4-0000-7000-8000-000000000030';
    level_intermediate_id UUID := '019e18e4-0000-7000-8000-000000000031';
    level_production_id UUID := '019e18e4-0000-7000-8000-000000000032';
    space_piano_id UUID := '019e18e4-0000-7000-8000-000000000040';
    space_theory_id UUID := '019e18e4-0000-7000-8000-000000000041';
    space_ensemble_id UUID := '019e18e4-0000-7000-8000-000000000042';
    space_recording_id UUID := '019e18e4-0000-7000-8000-000000000043';
    space_production_id UUID := '019e18e4-0000-7000-8000-000000000044';
    plan_space_piano_id UUID := '019e18e4-0000-7000-8000-000000000050';
    plan_space_theory_id UUID := '019e18e4-0000-7000-8000-000000000051';
    plan_space_ensemble_id UUID := '019e18e4-0000-7000-8000-000000000052';
    plan_space_recording_id UUID := '019e18e4-0000-7000-8000-000000000053';
    plan_space_production_id UUID := '019e18e4-0000-7000-8000-000000000054';
    piano_instrument_id UUID := '019e18e4-0000-7000-8000-000000000060';
    guitar_instrument_id UUID := '019e18e4-0000-7000-8000-000000000061';
    shift_morning_id UUID := '019e18e4-0000-7000-8000-000000000070';
    shift_afternoon_id UUID := '019e18e4-0000-7000-8000-000000000071';
    shift_evening_id UUID := '019e18e4-0000-7000-8000-000000000072';
    adult_applicant_id UUID := '019e18e4-0000-7000-8000-000000000101';
    tutor_id UUID := '019e18e4-0000-7000-8000-000000000102';
    dependent_id UUID := '019e18e4-0000-7000-8000-000000000103';
    submitted_applicant_id UUID := '019e18e4-0000-7000-8000-000000000104';
    approved_applicant_id UUID := '019e18e4-0000-7000-8000-000000000105';
    rejected_applicant_id UUID := '019e18e4-0000-7000-8000-000000000106';
    draft_application_id UUID := '019e18e4-0000-7000-8000-000000000201';
    submitted_application_id UUID := '019e18e4-0000-7000-8000-000000000202';
    approved_application_id UUID := '019e18e4-0000-7000-8000-000000000203';
    rejected_application_id UUID := '019e18e4-0000-7000-8000-000000000204';
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM institutions AS existing_institution
        WHERE existing_institution.institution_id = institution_id
    ) THEN
        RAISE EXCEPTION 'Enrollment demo seed requires institution %', institution_id;
    END IF;

    INSERT INTO people (
        person_id, institution_id, document_number, first_name, last_name,
        birth_date, phone_number, email, deleted, created_at, updated_at
    ) VALUES
        (adult_applicant_id, institution_id, '12345678', 'Ana', 'Garcia', '1990-04-12', '3515551001', 'ana.garcia@boero.local', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (tutor_id, institution_id, '23456789', 'Carlos', 'Gomez', '1985-08-21', '3515551002', 'carlos.gomez@boero.local', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (dependent_id, institution_id, '34567890', 'Sofia', 'Gomez', '2012-06-18', '3515551003', NULL, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (submitted_applicant_id, institution_id, '45678901', 'Lucia', 'Martinez', '1995-11-03', '3515551004', 'lucia.martinez@boero.local', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (approved_applicant_id, institution_id, '56789012', 'Mateo', 'Fernandez', '1992-02-27', '3515551005', 'mateo.fernandez@boero.local', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (rejected_applicant_id, institution_id, '67890123', 'Valentina', 'Lopez', '1998-09-15', '3515551006', 'valentina.lopez@boero.local', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON CONFLICT (person_id) DO UPDATE SET
        institution_id = EXCLUDED.institution_id,
        document_number = EXCLUDED.document_number,
        first_name = EXCLUDED.first_name,
        last_name = EXCLUDED.last_name,
        birth_date = EXCLUDED.birth_date,
        phone_number = EXCLUDED.phone_number,
        email = EXCLUDED.email,
        deleted = EXCLUDED.deleted,
        updated_at = CURRENT_TIMESTAMP;

    INSERT INTO person_guardians (
        person_guardian_id, institution_id, tutor_person_id, dependent_person_id,
        relationship, is_primary_contact, created_at, updated_at
    ) VALUES (
        '019e18e4-0000-7000-8000-000000000110', institution_id, tutor_id, dependent_id,
        'LEGAL_GUARDIAN', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (person_guardian_id) DO UPDATE SET
        relationship = EXCLUDED.relationship,
        is_primary_contact = EXCLUDED.is_primary_contact,
        updated_at = CURRENT_TIMESTAMP;

    INSERT INTO academic_years (
        academic_year_id, institution_id, "year", start_date, end_date, status,
        deleted_at, created_at, updated_at
    ) VALUES (
        academic_year_id, institution_id, 2026, '2026-01-01', '2026-12-31', 'ACTIVE',
        NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT ON CONSTRAINT academic_years_pkey DO UPDATE SET
        institution_id = EXCLUDED.institution_id,
        "year" = EXCLUDED."year",
        start_date = EXCLUDED.start_date,
        end_date = EXCLUDED.end_date,
        status = EXCLUDED.status,
        deleted_at = NULL,
        updated_at = CURRENT_TIMESTAMP;

    INSERT INTO enrollment_periods (
        enrollment_period_id, institution_id, academic_year_id, name,
        start_date, end_date, status, deleted_at, created_at, updated_at
    ) VALUES (
        period_id, institution_id, academic_year_id, 'Inscripciones demo 2026',
        '2026-01-15 00:00:00+00', '2026-12-15 23:59:59+00', 'OPEN',
        NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (enrollment_period_id) DO UPDATE SET
        institution_id = EXCLUDED.institution_id,
        academic_year_id = EXCLUDED.academic_year_id,
        name = EXCLUDED.name,
        start_date = EXCLUDED.start_date,
        end_date = EXCLUDED.end_date,
        status = EXCLUDED.status,
        deleted_at = NULL,
        updated_at = CURRENT_TIMESTAMP;

    INSERT INTO training_paths (
        training_path_id, institution_id, name, description, active,
        deleted_at, created_at, updated_at
    ) VALUES
        (training_path_music_id, institution_id, 'Formación Instrumental', 'Trayecto demo de formación instrumental.', true, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (training_path_production_id, institution_id, 'Producción Musical', 'Trayecto demo de producción y grabación.', true, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON CONFLICT (training_path_id) DO UPDATE SET
        institution_id = EXCLUDED.institution_id,
        name = EXCLUDED.name,
        description = EXCLUDED.description,
        active = EXCLUDED.active,
        deleted_at = NULL,
        updated_at = CURRENT_TIMESTAMP;

    INSERT INTO study_plans (
        study_plan_id, institution_id, training_path_id, previous_version_id,
        version_number, name, effective_from, effective_to, status,
        deleted_at, created_at, updated_at
    ) VALUES
        (study_plan_music_id, institution_id, training_path_music_id, NULL, 1, 'Plan Instrumental 2026', '2026-01-01', NULL, 'ACTIVE', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (study_plan_production_id, institution_id, training_path_production_id, NULL, 1, 'Plan Producción 2026', '2026-01-01', NULL, 'ACTIVE', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON CONFLICT (study_plan_id) DO UPDATE SET
        institution_id = EXCLUDED.institution_id,
        training_path_id = EXCLUDED.training_path_id,
        previous_version_id = EXCLUDED.previous_version_id,
        version_number = EXCLUDED.version_number,
        name = EXCLUDED.name,
        effective_from = EXCLUDED.effective_from,
        effective_to = EXCLUDED.effective_to,
        status = EXCLUDED.status,
        deleted_at = NULL,
        updated_at = CURRENT_TIMESTAMP;

    INSERT INTO academic_levels (
        academic_level_id, study_plan_id, name, display_order, description,
        created_at, updated_at
    ) VALUES
        (level_initial_id, study_plan_music_id, 'Nivel Inicial', 1, 'Primer nivel del plan instrumental.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (level_intermediate_id, study_plan_music_id, 'Nivel Intermedio', 2, 'Segundo nivel del plan instrumental.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (level_production_id, study_plan_production_id, 'Nivel Producción', 1, 'Nivel único del plan de producción.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON CONFLICT (academic_level_id) DO UPDATE SET
        study_plan_id = EXCLUDED.study_plan_id,
        name = EXCLUDED.name,
        display_order = EXCLUDED.display_order,
        description = EXCLUDED.description,
        updated_at = CURRENT_TIMESTAMP;

    INSERT INTO academic_spaces (
        academic_space_id, institution_id, name, description, type, format, active,
        deleted_at, created_at, updated_at
    ) VALUES
        (space_piano_id, institution_id, 'Instrumento Principal', 'Espacio instrumental con selección de instrumento.', 'SUBJECT', 'INDIVIDUAL', true, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (space_theory_id, institution_id, 'Lenguaje Musical', 'Espacio obligatorio de teoría musical.', 'SUBJECT', 'GRUPAL', true, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (space_ensemble_id, institution_id, 'Práctica de Conjunto', 'Taller opcional de práctica grupal.', 'WORKSHOP', 'GRUPAL', true, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (space_recording_id, institution_id, 'Grabación y Sonido', 'Espacio de producción y grabación.', 'PRACTICE', 'GRUPAL', true, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (space_production_id, institution_id, 'Producción Musical', 'Espacio obligatorio de producción.', 'SUBJECT', 'GRUPAL', true, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON CONFLICT (academic_space_id) DO UPDATE SET
        institution_id = EXCLUDED.institution_id,
        name = EXCLUDED.name,
        description = EXCLUDED.description,
        type = EXCLUDED.type,
        format = EXCLUDED.format,
        active = EXCLUDED.active,
        deleted_at = NULL,
        updated_at = CURRENT_TIMESTAMP;

    INSERT INTO study_plan_spaces (
        study_plan_space_id, institution_id, study_plan_id, academic_space_id,
        academic_level_id, requirement_type, display_order, approval_mode,
        created_at, updated_at
    ) VALUES
        (plan_space_piano_id, institution_id, study_plan_music_id, space_piano_id, level_initial_id, 'REQUIRED', 1, 'PROMOTION_OR_FINAL_EXAM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (plan_space_theory_id, institution_id, study_plan_music_id, space_theory_id, level_initial_id, 'REQUIRED', 2, 'PROMOTION', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (plan_space_ensemble_id, institution_id, study_plan_music_id, space_ensemble_id, level_intermediate_id, 'OPTIONAL', 1, 'PROMOTION', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (plan_space_recording_id, institution_id, study_plan_production_id, space_recording_id, level_production_id, 'OPTIONAL', 1, 'FINAL_EXAM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (plan_space_production_id, institution_id, study_plan_production_id, space_production_id, level_production_id, 'REQUIRED', 2, 'PROMOTION_OR_FINAL_EXAM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON CONFLICT (study_plan_space_id) DO UPDATE SET
        institution_id = EXCLUDED.institution_id,
        study_plan_id = EXCLUDED.study_plan_id,
        academic_space_id = EXCLUDED.academic_space_id,
        academic_level_id = EXCLUDED.academic_level_id,
        requirement_type = EXCLUDED.requirement_type,
        display_order = EXCLUDED.display_order,
        approval_mode = EXCLUDED.approval_mode,
        updated_at = CURRENT_TIMESTAMP;

    INSERT INTO instruments (
        instrument_id, institution_id, name, description, active,
        deleted_at, created_at, updated_at
    ) VALUES
        (piano_instrument_id, institution_id, 'Piano', 'Instrumento demo de teclado.', true, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (guitar_instrument_id, institution_id, 'Guitarra', 'Instrumento demo de cuerda.', true, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON CONFLICT (instrument_id) DO UPDATE SET
        institution_id = EXCLUDED.institution_id,
        name = EXCLUDED.name,
        description = EXCLUDED.description,
        active = EXCLUDED.active,
        deleted_at = NULL,
        updated_at = CURRENT_TIMESTAMP;

    INSERT INTO study_plan_space_instruments (
        study_plan_space_instrument_id, institution_id, study_plan_space_id,
        instrument_id, created_at, updated_at
    ) VALUES
        ('019e18e4-0000-7000-8000-000000000080', institution_id, plan_space_piano_id, piano_instrument_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        ('019e18e4-0000-7000-8000-000000000081', institution_id, plan_space_piano_id, guitar_instrument_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON CONFLICT (study_plan_space_instrument_id) DO UPDATE SET
        institution_id = EXCLUDED.institution_id,
        study_plan_space_id = EXCLUDED.study_plan_space_id,
        instrument_id = EXCLUDED.instrument_id,
        updated_at = CURRENT_TIMESTAMP;

    INSERT INTO shifts (
        shift_id, institution_id, name, description, active,
        deleted_at, created_at, updated_at
    ) VALUES
        (shift_morning_id, institution_id, 'Mañana', 'Turno de 08:00 a 12:00.', true, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (shift_afternoon_id, institution_id, 'Tarde', 'Turno de 14:00 a 18:00.', true, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (shift_evening_id, institution_id, 'Noche', 'Turno de 18:00 a 22:00.', true, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON CONFLICT (shift_id) DO UPDATE SET
        institution_id = EXCLUDED.institution_id,
        name = EXCLUDED.name,
        description = EXCLUDED.description,
        active = EXCLUDED.active,
        deleted_at = NULL,
        updated_at = CURRENT_TIMESTAMP;

    INSERT INTO enrollment_applications (
        enrollment_application_id, institution_id, applicant_person_id,
        submitted_by_person_id, study_plan_id, training_path_id, academic_year_id,
        enrollment_period_id, status, rejection_reason, resolved_at,
        resolved_by_person_id, deleted_at, created_at, updated_at
    ) VALUES
        (draft_application_id, institution_id, adult_applicant_id, adult_applicant_id, study_plan_music_id, training_path_music_id, academic_year_id, period_id, 'DRAFT', NULL, NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (submitted_application_id, institution_id, dependent_id, tutor_id, study_plan_production_id, training_path_production_id, academic_year_id, period_id, 'SUBMITTED', NULL, NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (approved_application_id, institution_id, approved_applicant_id, approved_applicant_id, study_plan_music_id, training_path_music_id, academic_year_id, period_id, 'APPROVED', NULL, CURRENT_TIMESTAMP, tutor_id, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (rejected_application_id, institution_id, rejected_applicant_id, rejected_applicant_id, study_plan_production_id, training_path_production_id, academic_year_id, period_id, 'REJECTED', 'Documentación demo incompleta.', CURRENT_TIMESTAMP, tutor_id, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON CONFLICT (enrollment_application_id) DO UPDATE SET
        institution_id = EXCLUDED.institution_id,
        applicant_person_id = EXCLUDED.applicant_person_id,
        submitted_by_person_id = EXCLUDED.submitted_by_person_id,
        study_plan_id = EXCLUDED.study_plan_id,
        training_path_id = EXCLUDED.training_path_id,
        academic_year_id = EXCLUDED.academic_year_id,
        enrollment_period_id = EXCLUDED.enrollment_period_id,
        status = EXCLUDED.status,
        rejection_reason = EXCLUDED.rejection_reason,
        resolved_at = EXCLUDED.resolved_at,
        resolved_by_person_id = EXCLUDED.resolved_by_person_id,
        deleted_at = NULL,
        updated_at = CURRENT_TIMESTAMP;

    INSERT INTO applicant_education_backgrounds (
        applicant_education_background_id, enrollment_application_id,
        secondary_school, school_origin, current_grade_year,
        secondary_completed, secondary_degree_title, deleted_at,
        created_at, updated_at
    ) VALUES
        ('019e18e4-0000-7000-8000-000000000301', submitted_application_id, 'Instituto Secundario Demo', 'Privado', NULL, true, 'Bachiller', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        ('019e18e4-0000-7000-8000-000000000302', approved_application_id, 'Colegio Provincial Demo', 'Público', NULL, true, 'Bachiller', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        ('019e18e4-0000-7000-8000-000000000303', rejected_application_id, 'Escuela Demo', 'Público', NULL, false, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON CONFLICT (applicant_education_background_id) DO UPDATE SET
        enrollment_application_id = EXCLUDED.enrollment_application_id,
        secondary_school = EXCLUDED.secondary_school,
        school_origin = EXCLUDED.school_origin,
        current_grade_year = EXCLUDED.current_grade_year,
        secondary_completed = EXCLUDED.secondary_completed,
        secondary_degree_title = EXCLUDED.secondary_degree_title,
        deleted_at = NULL,
        updated_at = CURRENT_TIMESTAMP;

    INSERT INTO applicant_responsibles (
        applicant_responsible_id, enrollment_application_id, full_name,
        document_number, occupation, phone_number, email, education_level,
        deleted_at, created_at, updated_at
    ) VALUES (
        '019e18e4-0000-7000-8000-000000000311', submitted_application_id,
        'Carlos Gomez', '23456789', 'Docente', '3515551002',
        'carlos.gomez@boero.local', 'UNIVERSITY_COMPLETE', NULL,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (applicant_responsible_id) DO UPDATE SET
        enrollment_application_id = EXCLUDED.enrollment_application_id,
        full_name = EXCLUDED.full_name,
        document_number = EXCLUDED.document_number,
        occupation = EXCLUDED.occupation,
        phone_number = EXCLUDED.phone_number,
        email = EXCLUDED.email,
        education_level = EXCLUDED.education_level,
        deleted_at = NULL,
        updated_at = CURRENT_TIMESTAMP;

    INSERT INTO applicant_health_inclusions (
        applicant_health_inclusion_id, enrollment_application_id,
        receives_reasonable_adjustments, adjustment_details, deleted_at,
        created_at, updated_at
    ) VALUES
        ('019e18e4-0000-7000-8000-000000000321', submitted_application_id, false, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        ('019e18e4-0000-7000-8000-000000000322', approved_application_id, false, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        ('019e18e4-0000-7000-8000-000000000323', rejected_application_id, false, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON CONFLICT (applicant_health_inclusion_id) DO UPDATE SET
        enrollment_application_id = EXCLUDED.enrollment_application_id,
        receives_reasonable_adjustments = EXCLUDED.receives_reasonable_adjustments,
        adjustment_details = EXCLUDED.adjustment_details,
        deleted_at = NULL,
        updated_at = CURRENT_TIMESTAMP;

    INSERT INTO applicant_preferences (
        applicant_preference_id, enrollment_application_id, preferred_shift,
        allows_image_use, is_reenrolling, previous_teacher, deleted_at,
        created_at, updated_at
    ) VALUES
        ('019e18e4-0000-7000-8000-000000000331', submitted_application_id, 'Tarde', false, false, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        ('019e18e4-0000-7000-8000-000000000332', approved_application_id, 'Mañana', true, true, 'Laura Docente', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        ('019e18e4-0000-7000-8000-000000000333', rejected_application_id, 'Noche', false, false, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON CONFLICT (applicant_preference_id) DO UPDATE SET
        enrollment_application_id = EXCLUDED.enrollment_application_id,
        preferred_shift = EXCLUDED.preferred_shift,
        allows_image_use = EXCLUDED.allows_image_use,
        is_reenrolling = EXCLUDED.is_reenrolling,
        previous_teacher = EXCLUDED.previous_teacher,
        deleted_at = NULL,
        updated_at = CURRENT_TIMESTAMP;

    INSERT INTO enrollment_application_spaces (
        enrollment_application_space_id, enrollment_application_id,
        study_plan_space_id, instrument_id, created_at, updated_at
    ) VALUES
        ('019e18e4-0000-7000-8000-000000000341', submitted_application_id, plan_space_production_id, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        ('019e18e4-0000-7000-8000-000000000342', approved_application_id, plan_space_piano_id, piano_instrument_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        ('019e18e4-0000-7000-8000-000000000343', approved_application_id, plan_space_theory_id, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        ('019e18e4-0000-7000-8000-000000000344', rejected_application_id, plan_space_production_id, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON CONFLICT (enrollment_application_space_id) DO UPDATE SET
        enrollment_application_id = EXCLUDED.enrollment_application_id,
        study_plan_space_id = EXCLUDED.study_plan_space_id,
        instrument_id = EXCLUDED.instrument_id,
        updated_at = CURRENT_TIMESTAMP;

    INSERT INTO students (
        student_id, institution_id, person_id, enrollment_date, status,
        file_number, created_at, updated_at
    ) VALUES (
        '019e18e4-0000-7000-8000-000000000351', institution_id,
        approved_applicant_id, '2026-02-15', 'ACTIVE', '2026-90001',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (student_id) DO UPDATE SET
        institution_id = EXCLUDED.institution_id,
        person_id = EXCLUDED.person_id,
        enrollment_date = EXCLUDED.enrollment_date,
        status = EXCLUDED.status,
        file_number = EXCLUDED.file_number,
        updated_at = CURRENT_TIMESTAMP;
END $$;
