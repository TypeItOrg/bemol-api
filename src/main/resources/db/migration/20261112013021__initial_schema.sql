-- Consolidated schema for fresh installations; replaces the historical migration chain.
-- SQL functions reference tables created later in this script.
SET LOCAL check_function_bodies = false;

CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

CREATE FUNCTION audit_events_reject_change() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$;

CREATE FUNCTION bemol_normalize_text(value text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    RETURN lower(translate(COALESCE(value, ''::text), 'áéíóúüñÁÉÍÓÚÜÑ'::text, 'aeiouunAEIOUUN'::text));

CREATE FUNCTION bemol_search_vector(value text) RETURNS tsvector
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    RETURN to_tsvector('simple'::regconfig, bemol_normalize_text(value));

CREATE FUNCTION bemol_search_rank(value text, query text, normalized_query text) RETURNS real
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    RETURN ((CASE WHEN (bemol_normalize_text(value) = normalized_query) THEN 10 ELSE 0 END)::double precision + ts_rank_cd(bemol_search_vector(value), to_tsquery('simple'::regconfig, query)));

CREATE FUNCTION check_enrollment_document_delivery_gate() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE parent enrollment_applications%ROWTYPE;
BEGIN
 SELECT * INTO parent FROM enrollment_applications WHERE enrollment_application_id=NEW.enrollment_application_id;
 IF parent.status IN ('SUBMITTED','PROVISIONALLY_APPROVED','APPROVED') AND EXISTS(
 SELECT 1 FROM enrollment_document_requirements r WHERE r.application_id=parent.enrollment_application_id AND r.active
 AND ((r.origin='ORIGINAL' AND r.level='AT_SUBMISSION') OR (parent.status='APPROVED' AND r.level<>'OPTIONAL'))
 AND NOT EXISTS(SELECT 1 FROM enrollment_attachments f WHERE f.requirement_id=r.id AND f.version_status='CURRENT' AND f.deleted_at IS NULL
 AND r.allowed_formats ? f.content_type AND (parent.status='SUBMITTED' OR f.review_status='ACCEPTED'))) THEN
 RAISE EXCEPTION 'Required documentation incomplete' USING ERRCODE='23514',CONSTRAINT='enrollment_document_gate_check'; END IF;
 RETURN NULL;
END $$;

CREATE FUNCTION clear_draft_courses_after_period_scope_reduction() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    WITH removed AS (
        DELETE FROM enrollment_application_courses selection
        USING enrollment_applications application, courses course, study_plan_spaces space
        WHERE application.enrollment_application_id = selection.enrollment_application_id
          AND application.status = 'DRAFT' AND application.deleted_at IS NULL
          AND selection.enrollment_period_id = OLD.enrollment_period_id
          AND course.course_id = selection.course_id
          AND space.study_plan_space_id = course.study_plan_space_id
          AND space.study_plan_id = OLD.study_plan_id
          AND space.academic_level_id IS NOT DISTINCT FROM OLD.academic_level_id
        RETURNING selection.enrollment_application_id
    )
    UPDATE enrollment_applications SET updated_at = CURRENT_TIMESTAMP
    WHERE enrollment_application_id IN (SELECT enrollment_application_id FROM removed);
    RETURN NULL;
END $$;

CREATE FUNCTION derive_enrollment_period_level() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE period enrollment_periods%ROWTYPE; offering enrollment_period_offerings%ROWTYPE;
BEGIN
    IF TG_OP = 'UPDATE' AND (OLD.offering_id <> NEW.offering_id OR OLD.academic_level_id IS DISTINCT FROM NEW.academic_level_id) THEN
        RAISE EXCEPTION 'Scope context is immutable' USING ERRCODE = '23514', CONSTRAINT = 'enrollment_period_scope_invalid';
    END IF;
    SELECT * INTO offering FROM enrollment_period_offerings WHERE offering_id = NEW.offering_id;
    SELECT * INTO period FROM enrollment_periods WHERE enrollment_period_id = offering.enrollment_period_id FOR UPDATE;
    NEW.study_plan_id := offering.study_plan_id;
    NEW.institution_id := period.institution_id;
    NEW.academic_year_id := period.academic_year_id;
    NEW.enrollment_period_id := period.enrollment_period_id;
    NEW.availability := tstzrange(period.start_date, period.end_date, '[]');
    NEW.reserves := period.deleted_at IS NULL AND period.status IN ('PLANNED', 'OPEN');
    RETURN NEW;
END $$;

CREATE FUNCTION enforce_institutional_authority_scope() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.access_scope <> 'INSTITUTION' AND EXISTS (
        SELECT 1 FROM roles WHERE role_id = NEW.role_id AND is_system = true AND code = 'INSTITUTIONAL_AUTHORITY'
    ) THEN
        RAISE EXCEPTION 'Institutional authority requires institution scope' USING ERRCODE = '23514',
            CONSTRAINT = 'person_role_assignment_scope_consistency';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION enrollment_prerequisites_satisfied(tenant_id uuid, applicant_id uuid, target_course_id uuid) RETURNS boolean
    LANGUAGE sql STABLE
    AS $$
    SELECT NOT EXISTS (
        SELECT 1 FROM prerequisites requirement
        JOIN courses target ON target.study_plan_space_id = requirement.target_study_plan_space_id
        WHERE target.course_id = target_course_id AND target.institution_id = tenant_id
          AND requirement.requirement_stage = 'TO_ENROLL'
          AND NOT EXISTS (
              SELECT 1 FROM course_enrollments evidence
              JOIN students student ON student.student_id = evidence.student_id AND student.institution_id = tenant_id
              JOIN courses prior_course ON prior_course.course_id = evidence.course_id AND prior_course.institution_id = tenant_id
              WHERE evidence.institution_id = tenant_id AND student.person_id = applicant_id
                AND prior_course.study_plan_space_id = requirement.required_study_plan_space_id
                AND evidence.status IN ('ENROLLED', 'COMPLETED')
                AND (evidence.academic_status IN ('PROMOTED', 'PASSED')
                    OR requirement.required_condition = 'REGULAR' AND evidence.academic_status = 'REGULARIZED')
          )
    );
$$;

CREATE FUNCTION prepare_role_scope_training_path() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.person_role_assignment_id <> OLD.person_role_assignment_id THEN
        RAISE EXCEPTION 'Assignment scope relations cannot be transferred' USING ERRCODE = '23514',
            CONSTRAINT = 'person_role_assignment_scope_consistency';
    END IF;
    SELECT institution_id INTO NEW.institution_id FROM person_role_assignments
    WHERE person_role_assignment_id = NEW.person_role_assignment_id FOR UPDATE;
    RETURN NEW;
END;
$$;

CREATE FUNCTION preserve_enrollment_period_context() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.institution_id <> NEW.institution_id OR OLD.academic_year_id <> NEW.academic_year_id
        OR (OLD.scope_configured AND NOT NEW.scope_configured) THEN
        RAISE EXCEPTION 'Period context is immutable' USING ERRCODE = '23514', CONSTRAINT = 'enrollment_period_scope_invalid';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION prevent_enrollment_period_scope_shrink() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE period_id uuid; configured boolean;
BEGIN
    period_id := OLD.enrollment_period_id;
    SELECT scope_configured INTO configured FROM enrollment_periods WHERE enrollment_period_id = period_id FOR UPDATE;
    IF configured AND EXISTS (
        SELECT 1 FROM enrollment_applications application
        WHERE application.status = 'SUBMITTED' AND application.deleted_at IS NULL
          AND (application.enrollment_period_id = period_id OR EXISTS (
              SELECT 1 FROM enrollment_application_courses selection
              WHERE selection.enrollment_application_id = application.enrollment_application_id
                AND selection.enrollment_period_id = period_id))
    ) THEN
        RAISE EXCEPTION 'Scope cannot shrink with submitted applications'
            USING ERRCODE = '23514', CONSTRAINT = 'enrollment_period_scope_shrink';
    END IF;
    RETURN OLD;
END $$;

CREATE FUNCTION protect_document_definition_institution() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF OLD.used_at IS NOT NULL AND NEW.used_at IS DISTINCT FROM OLD.used_at THEN
    RAISE EXCEPTION 'Document usage history is immutable'
      USING ERRCODE = '23514', CONSTRAINT = 'document_definition_usage_immutable_check';
  END IF;
  IF NEW.institution_id IS DISTINCT FROM OLD.institution_id AND (
    OLD.used_at IS NOT NULL
    OR EXISTS (SELECT 1 FROM training_path_document_requirements r WHERE r.document_id = OLD.id)
    OR EXISTS (SELECT 1 FROM enrollment_document_requirements r WHERE r.document_id = OLD.id)
  ) THEN
    RAISE EXCEPTION 'Used document cannot change institution'
      USING ERRCODE = '23514', CONSTRAINT = 'document_definition_institution_change_check';
  END IF;
  RETURN NEW;
END $$;

CREATE FUNCTION protect_enrollment_application_form() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF NEW.form_version_id IS DISTINCT FROM OLD.form_version_id THEN
            RAISE EXCEPTION 'The application form version is immutable (constraint "enrollment_application_form_immutable_check")'
                USING ERRCODE = '23514', CONSTRAINT = 'enrollment_application_form_immutable_check';
        END IF;
        IF OLD.form_version_id IS NOT NULL AND (NEW.training_path_id IS DISTINCT FROM OLD.training_path_id
            OR NEW.academic_year_id IS DISTINCT FROM OLD.academic_year_id) THEN
            RAISE EXCEPTION 'The versioned application context is immutable (constraint "enrollment_application_form_context_check")'
                USING ERRCODE = '23514', CONSTRAINT = 'enrollment_application_form_context_check';
        END IF;
        IF (OLD.status <> 'DRAFT' OR OLD.deleted_at IS NOT NULL)
            AND NEW.custom_answers IS DISTINCT FROM OLD.custom_answers THEN
            RAISE EXCEPTION 'Submitted application answers are immutable (constraint "enrollment_application_answers_frozen_check")'
                USING ERRCODE = '23514', CONSTRAINT = 'enrollment_application_answers_frozen_check';
        END IF;
    ELSIF NEW.form_version_id IS NOT NULL THEN
        PERFORM 1 FROM enrollment_form_versions
            WHERE id = NEW.form_version_id AND institution_id = NEW.institution_id AND status = 'PUBLISHED'
            FOR SHARE;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'New applications require a published enrollment version (constraint "enrollment_application_form_published_check")'
                USING ERRCODE = '23514', CONSTRAINT = 'enrollment_application_form_published_check';
        END IF;
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION protect_enrollment_document_delivery() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE parent_status varchar(40);
BEGIN
 SELECT status INTO parent_status FROM enrollment_applications
 WHERE enrollment_application_id=NEW.enrollment_application_id FOR UPDATE;
 IF parent_status NOT IN ('DRAFT','SUBMITTED','PROVISIONALLY_APPROVED') THEN
  RAISE EXCEPTION 'Document delivery is locked' USING ERRCODE='23514',CONSTRAINT='enrollment_attachment_immutable_check';
 END IF;
 IF TG_OP='UPDATE' AND (
   OLD.version_status <> 'CURRENT' OR OLD.review_status='ACCEPTED'
   OR OLD.requirement_id IS DISTINCT FROM NEW.requirement_id
   OR OLD.enrollment_application_id IS DISTINCT FROM NEW.enrollment_application_id
   OR OLD.storage_path IS DISTINCT FROM NEW.storage_path
   OR OLD.original_file_name IS DISTINCT FROM NEW.original_file_name
   OR OLD.content_type IS DISTINCT FROM NEW.content_type
   OR OLD.file_size IS DISTINCT FROM NEW.file_size
   OR OLD.uploaded_by IS DISTINCT FROM NEW.uploaded_by
   OR OLD.uploader_type IS DISTINCT FROM NEW.uploader_type
   OR OLD.deleted_at IS DISTINCT FROM NEW.deleted_at
 ) THEN
  RAISE EXCEPTION 'Document delivery is immutable' USING ERRCODE='23514',CONSTRAINT='enrollment_attachment_immutable_check';
 END IF;
 RETURN NEW;
END $$;

CREATE FUNCTION protect_enrollment_document_request() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE parent_status varchar(40);
BEGIN
 IF TG_OP <> 'INSERT' THEN RAISE EXCEPTION 'Document request is immutable' USING ERRCODE='23514',CONSTRAINT='enrollment_document_request_immutable_check'; END IF;
 SELECT status INTO parent_status FROM enrollment_applications WHERE enrollment_application_id=NEW.application_id FOR UPDATE;
 IF parent_status NOT IN ('SUBMITTED','PROVISIONALLY_APPROVED') THEN RAISE EXCEPTION 'Document request is closed' USING ERRCODE='23514',CONSTRAINT='enrollment_document_request_closed_check'; END IF;
 RETURN NEW;
END $$;

CREATE FUNCTION protect_enrollment_form_assignment() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF NEW.id IS DISTINCT FROM OLD.id OR NEW.institution_id IS DISTINCT FROM OLD.institution_id
            OR NEW.training_path_id IS DISTINCT FROM OLD.training_path_id
            OR NEW.academic_year_id IS DISTINCT FROM OLD.academic_year_id THEN
            RAISE EXCEPTION 'Enrollment form assignment context is immutable (constraint "enrollment_form_assignments_identity_check")'
                USING ERRCODE = '23514', CONSTRAINT = 'enrollment_form_assignments_identity_check';
        END IF;
    END IF;
    -- The shared lock orders assignment against concurrent archival.
    PERFORM 1 FROM enrollment_form_versions
        WHERE id = NEW.form_version_id AND institution_id = NEW.institution_id AND status = 'PUBLISHED'
        FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Only published enrollment versions can be assigned (constraint "enrollment_form_assignments_published_check")'
            USING ERRCODE = '23514', CONSTRAINT = 'enrollment_form_assignments_published_check';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION protect_enrollment_form_identity() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.institution_id IS DISTINCT FROM OLD.institution_id THEN
        RAISE EXCEPTION 'Enrollment form identity is immutable (constraint "enrollment_forms_identity_check")'
            USING ERRCODE = '23514', CONSTRAINT = 'enrollment_forms_identity_check';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION protect_enrollment_form_version() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'Published enrollment versions cannot be deleted (constraint "enrollment_form_versions_immutable_check")'
                USING ERRCODE = '23514', CONSTRAINT = 'enrollment_form_versions_immutable_check';
        END IF;
        RETURN OLD;
    END IF;
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'Enrollment versions must start as drafts (constraint "enrollment_form_versions_transition_check")'
                USING ERRCODE = '23514', CONSTRAINT = 'enrollment_form_versions_transition_check';
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.institution_id IS DISTINCT FROM OLD.institution_id
        OR NEW.form_id IS DISTINCT FROM OLD.form_id OR NEW.version_number IS DISTINCT FROM OLD.version_number THEN
        RAISE EXCEPTION 'Enrollment version identity is immutable (constraint "enrollment_form_versions_identity_check")'
            USING ERRCODE = '23514', CONSTRAINT = 'enrollment_form_versions_identity_check';
    END IF;
    IF OLD.status <> 'DRAFT' AND (NEW.definition IS DISTINCT FROM OLD.definition
        OR NEW.published_at IS DISTINCT FROM OLD.published_at)
        OR OLD.status = 'ARCHIVED' AND NEW.archived_at IS DISTINCT FROM OLD.archived_at THEN
        RAISE EXCEPTION 'Published enrollment definitions are immutable (constraint "enrollment_form_versions_immutable_check")'
            USING ERRCODE = '23514', CONSTRAINT = 'enrollment_form_versions_immutable_check';
    END IF;
    IF NEW.status IS DISTINCT FROM OLD.status AND NOT (
        OLD.status = 'DRAFT' AND NEW.status = 'PUBLISHED'
        OR OLD.status = 'PUBLISHED' AND NEW.status = 'ARCHIVED'
    ) THEN
        RAISE EXCEPTION 'Invalid enrollment version transition (constraint "enrollment_form_versions_transition_check")'
            USING ERRCODE = '23514', CONSTRAINT = 'enrollment_form_versions_transition_check';
    END IF;
    IF NEW.status = 'ARCHIVED' AND OLD.status <> 'ARCHIVED' AND EXISTS (
        SELECT 1 FROM enrollment_form_assignments WHERE form_version_id = OLD.id
    ) THEN
        RAISE EXCEPTION 'Assigned enrollment versions cannot be archived (constraint "enrollment_form_versions_assigned_check")'
            USING ERRCODE = '23514', CONSTRAINT = 'enrollment_form_versions_assigned_check';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION protect_enrollment_requirement_snapshot() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE parent_status varchar(40);
BEGIN
 SELECT status INTO parent_status FROM enrollment_applications WHERE enrollment_application_id=NEW.application_id FOR UPDATE;
 IF TG_OP='INSERT' AND NEW.origin='ADDITIONAL' THEN
  IF parent_status NOT IN ('SUBMITTED','PROVISIONALLY_APPROVED') THEN RAISE EXCEPTION 'Document request is closed' USING ERRCODE='23514',CONSTRAINT='enrollment_document_request_closed_check'; END IF;
  IF NOT NEW.active OR NOT EXISTS(SELECT 1 FROM document_definitions d WHERE d.id=NEW.document_id AND d.institution_id=NEW.institution_id AND d.active) THEN
   RAISE EXCEPTION 'Document request is inactive' USING ERRCODE='23514',CONSTRAINT='enrollment_document_request_inactive_check';
  END IF;
 ELSIF parent_status <> 'DRAFT' OR NEW.origin <> 'ORIGINAL' THEN
  RAISE EXCEPTION 'Enrollment snapshot is frozen' USING ERRCODE='23514',CONSTRAINT='enrollment_requirement_frozen_check';
 END IF;
 IF TG_OP='UPDATE' AND (NEW.application_id IS DISTINCT FROM OLD.application_id OR NEW.source_requirement_id IS DISTINCT FROM OLD.source_requirement_id
 OR NEW.document_id IS DISTINCT FROM OLD.document_id OR NEW.institution_id IS DISTINCT FROM OLD.institution_id OR NEW.origin IS DISTINCT FROM OLD.origin OR NEW.request_id IS DISTINCT FROM OLD.request_id) THEN
  RAISE EXCEPTION 'Enrollment requirement identity is immutable' USING ERRCODE='23514',CONSTRAINT='enrollment_requirement_identity_check';
 END IF;
 RETURN NEW;
END $$;

CREATE FUNCTION protect_instantiated_academic_space() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF (NEW.instrumental IS DISTINCT FROM OLD.instrumental
        AND EXISTS (SELECT 1 FROM courses
            WHERE institution_id = NEW.institution_id
              AND academic_space_id = NEW.academic_space_id AND deleted_at IS NULL))
       OR (NEW.format IS DISTINCT FROM OLD.format
        AND EXISTS (SELECT 1 FROM courses
            WHERE institution_id = NEW.institution_id
              AND academic_space_id = NEW.academic_space_id)) THEN
        RAISE EXCEPTION 'Academic space already instantiated'
            USING ERRCODE = '23514', CONSTRAINT = 'academic_spaces_instantiated_shape_check';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION protect_training_path_document_assignment() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
 IF NEW.document_id IS DISTINCT FROM OLD.document_id OR NEW.training_path_id IS DISTINCT FROM OLD.training_path_id OR NEW.institution_id IS DISTINCT FROM OLD.institution_id THEN
  RAISE EXCEPTION 'Document assignment identity is immutable' USING ERRCODE='23514',CONSTRAINT='training_path_document_identity_check';
 END IF;
 RETURN NEW;
END $$;

CREATE FUNCTION remember_document_definition_usage() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  UPDATE document_definitions SET used_at = COALESCE(used_at, CURRENT_TIMESTAMP)
  WHERE id = NEW.document_id AND institution_id = NEW.institution_id;
  RETURN NEW;
END $$;

CREATE FUNCTION require_configured_period_offerings() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE period_id uuid;
BEGIN
    IF TG_TABLE_NAME = 'enrollment_periods' THEN
        period_id := NEW.enrollment_period_id;
    ELSE
        period_id := OLD.enrollment_period_id;
    END IF;
    IF EXISTS (SELECT 1 FROM enrollment_periods WHERE enrollment_period_id = period_id AND scope_configured)
       AND (NOT EXISTS (SELECT 1 FROM enrollment_period_offering_levels WHERE enrollment_period_id = period_id)
         OR EXISTS (SELECT 1 FROM enrollment_period_offerings offering WHERE offering.enrollment_period_id = period_id
              AND NOT EXISTS (SELECT 1 FROM enrollment_period_offering_levels level WHERE level.offering_id = offering.offering_id))) THEN
        RAISE EXCEPTION 'Configured period requires an offering' USING ERRCODE = '23514', CONSTRAINT = 'enrollment_period_scope_invalid';
    END IF;
    RETURN NULL;
END $$;

CREATE FUNCTION sync_enrollment_period_reservations() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    UPDATE enrollment_period_offering_levels SET reserves = reserves WHERE enrollment_period_id = NEW.enrollment_period_id;
    RETURN NEW;
END $$;

CREATE FUNCTION validate_academic_enrollment_exception() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'Academic authorizations are no longer supported'
        USING ERRCODE = '23514', CONSTRAINT = 'academic_exception_immutable';
END $$;

CREATE FUNCTION validate_active_enrollment_delivery() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE requirement enrollment_document_requirements%ROWTYPE;
BEGIN
 SELECT * INTO requirement FROM enrollment_document_requirements WHERE id=NEW.requirement_id;
 IF NOT requirement.active THEN RAISE EXCEPTION 'Requirement is retired' USING ERRCODE='23514',CONSTRAINT='enrollment_requirement_retired_check'; END IF;
 IF NEW.version_status='CURRENT' AND NOT (requirement.allowed_formats ? NEW.content_type) THEN
  RAISE EXCEPTION 'File format is not allowed' USING ERRCODE='23514',CONSTRAINT='enrollment_document_format_check';
 END IF;
 RETURN NEW;
END $$;

CREATE FUNCTION validate_application_academic_requirements() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.status <> 'SUBMITTED' THEN
        RETURN NEW;
    END IF;
    IF TG_OP = 'UPDATE' AND OLD.status = 'SUBMITTED' THEN
        RETURN NEW;
    END IF;
    PERFORM 1 FROM institutions WHERE institution_id = NEW.institution_id FOR UPDATE;
    IF EXISTS (
        SELECT 1 FROM enrollment_application_courses selection
        WHERE selection.enrollment_application_id = NEW.enrollment_application_id
          AND NOT enrollment_prerequisites_satisfied(NEW.institution_id, NEW.applicant_person_id, selection.course_id)
    ) THEN
        RAISE EXCEPTION 'Academic requirements are pending' USING ERRCODE = '23514', CONSTRAINT = 'enrollment_application_academic_requirements_check';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION validate_course_enrollment_academic_requirements() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE applicant_id uuid;
BEGIN
    PERFORM 1 FROM institutions WHERE institution_id = NEW.institution_id FOR UPDATE;
    IF NEW.status <> 'ENROLLED' THEN
        RETURN NEW;
    END IF;
    -- Updating an existing result must not invalidate historical enrollments.
    IF TG_OP = 'UPDATE' AND OLD.status = 'ENROLLED' THEN
        RETURN NEW;
    END IF;
    SELECT person_id INTO applicant_id FROM students WHERE student_id = NEW.student_id AND institution_id = NEW.institution_id;
    IF NOT enrollment_prerequisites_satisfied(NEW.institution_id, applicant_id, NEW.course_id) THEN
        RAISE EXCEPTION 'Academic requirements are pending' USING ERRCODE = '23514', CONSTRAINT = 'course_enrollment_academic_requirements_check';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION validate_course_enrollment_assignment() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE enrollment course_enrollments%ROWTYPE; schedule course_class_schedules%ROWTYPE;
        day course_class_days%ROWTYPE; slot course_individual_slots%ROWTYPE; occupied integer;
BEGIN
    PERFORM 1 FROM institutions WHERE institution_id = NEW.institution_id FOR UPDATE;
    SELECT * INTO enrollment FROM course_enrollments WHERE course_enrollment_id = NEW.course_enrollment_id;
    SELECT * INTO schedule FROM course_class_schedules WHERE course_class_schedule_id = NEW.course_class_schedule_id;
    SELECT * INTO day FROM course_class_days WHERE course_class_day_id = schedule.course_class_day_id;
    IF enrollment.course_class_id IS DISTINCT FROM day.course_class_id OR NEW.day_of_week IS DISTINCT FROM day.day_of_week
       OR (day.period_duration_minutes IS NOT NULL) IS DISTINCT FROM (NEW.course_individual_slot_id IS NOT NULL) THEN
        RAISE EXCEPTION 'Invalid assignment' USING ERRCODE = '23514', CONSTRAINT = 'course_enrollment_schedules_context_check';
    END IF;
    IF NEW.course_individual_slot_id IS NOT NULL THEN
        SELECT * INTO slot FROM course_individual_slots WHERE course_individual_slot_id = NEW.course_individual_slot_id;
        IF NEW.start_time IS DISTINCT FROM slot.start_time OR NEW.end_time IS DISTINCT FROM slot.end_time THEN
            RAISE EXCEPTION 'Invalid slot time' USING ERRCODE = '23514', CONSTRAINT = 'course_enrollment_schedules_context_check';
        END IF;
    ELSIF NEW.start_time IS DISTINCT FROM schedule.start_time OR NEW.end_time IS DISTINCT FROM schedule.end_time THEN
        RAISE EXCEPTION 'Invalid group time' USING ERRCODE = '23514', CONSTRAINT = 'course_enrollment_schedules_context_check';
    END IF;
    IF NEW.released_at IS NULL THEN
        IF EXISTS (SELECT 1 FROM course_enrollment_schedules other
            JOIN course_class_schedules other_schedule USING (course_class_schedule_id)
            WHERE other.course_enrollment_id = NEW.course_enrollment_id
              AND other_schedule.course_class_day_id = day.course_class_day_id
              AND other.course_enrollment_schedule_id <> NEW.course_enrollment_schedule_id AND other.released_at IS NULL) THEN
            RAISE EXCEPTION 'Duplicate day' USING ERRCODE = '23505', CONSTRAINT = 'course_enrollment_schedules_active_day_unique';
        END IF;
        SELECT count(*) INTO occupied FROM course_enrollment_schedules other
            JOIN course_class_schedules other_schedule USING (course_class_schedule_id)
            WHERE other.institution_id = NEW.institution_id AND other.released_at IS NULL
              AND other.course_enrollment_schedule_id <> NEW.course_enrollment_schedule_id
              AND other_schedule.course_class_day_id = day.course_class_day_id;
        IF day.capacity IS NOT NULL AND occupied >= day.capacity THEN
            RAISE EXCEPTION 'No capacity' USING ERRCODE = '23514', CONSTRAINT = 'course_enrollment_schedules_capacity_check';
        END IF;
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION validate_course_enrollment_parent() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.source = 'APPLICATION' AND NOT EXISTS (
        SELECT 1 FROM enrollment_application_courses child
        JOIN enrollment_applications parent USING (enrollment_application_id)
        JOIN students student ON student.person_id = parent.applicant_person_id AND student.institution_id = parent.institution_id
        WHERE child.enrollment_application_course_id = NEW.enrollment_application_course_id
          AND child.enrollment_application_id = NEW.enrollment_application_id AND child.course_id = NEW.course_id
          AND parent.status IN ('APPROVED','PROVISIONALLY_APPROVED') AND student.student_id = NEW.student_id) THEN
        RAISE EXCEPTION 'Invalid enrollment origin' USING ERRCODE = '23514', CONSTRAINT = 'course_enrollments_origin_check';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION validate_course_instrument_integrity() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE placement study_plan_spaces%ROWTYPE; space academic_spaces%ROWTYPE; path uuid;
BEGIN
    SELECT * INTO placement FROM study_plan_spaces WHERE study_plan_space_id = NEW.study_plan_space_id;
    SELECT * INTO space FROM academic_spaces WHERE academic_space_id = placement.academic_space_id FOR SHARE;
    SELECT training_path_id INTO path FROM study_plans WHERE study_plan_id = placement.study_plan_id;
    IF placement.institution_id IS DISTINCT FROM NEW.institution_id
       OR placement.academic_space_id IS DISTINCT FROM NEW.academic_space_id
       OR placement.academic_level_id IS DISTINCT FROM NEW.academic_level_id
       OR path IS DISTINCT FROM NEW.training_path_id
       OR (NEW.deleted_at IS NULL AND space.instrumental IS DISTINCT FROM (NEW.instrument_id IS NOT NULL)) THEN
        RAISE EXCEPTION 'Invalid curriculum or instrument'
            USING ERRCODE = '23514', CONSTRAINT = 'courses_curriculum_instrument_check';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION validate_course_selection_integrity() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE parent enrollment_applications%ROWTYPE; selected courses%ROWTYPE;
BEGIN
    PERFORM 1 FROM institutions WHERE institution_id = NEW.institution_id FOR UPDATE;
    SELECT * INTO parent FROM enrollment_applications WHERE enrollment_application_id = NEW.enrollment_application_id;
    SELECT * INTO selected FROM courses WHERE course_id = NEW.course_id;
    IF parent.institution_id IS DISTINCT FROM NEW.institution_id
       OR selected.institution_id IS DISTINCT FROM NEW.institution_id
       OR (parent.academic_year_id IS NOT NULL AND selected.academic_year_id IS DISTINCT FROM parent.academic_year_id)
       OR selected.training_path_id IS DISTINCT FROM parent.training_path_id THEN
        RAISE EXCEPTION 'Invalid course context' USING ERRCODE = '23514', CONSTRAINT = 'enrollment_application_courses_context_check';
    END IF;
    IF NEW.status IN ('PENDING', 'WAITLISTED') AND parent.status NOT IN ('CANCELLED', 'REJECTED')
       AND EXISTS (SELECT 1 FROM enrollment_application_courses other
           JOIN enrollment_applications app ON app.enrollment_application_id = other.enrollment_application_id
           WHERE other.institution_id = NEW.institution_id AND other.course_id = NEW.course_id
             AND other.enrollment_application_course_id <> NEW.enrollment_application_course_id
             AND app.applicant_person_id = parent.applicant_person_id AND app.deleted_at IS NULL
             AND app.status NOT IN ('CANCELLED', 'REJECTED') AND other.status IN ('PENDING', 'WAITLISTED')) THEN
        RAISE EXCEPTION 'Course already requested' USING ERRCODE = '23505', CONSTRAINT = 'enrollment_application_courses_active_applicant_course_unique';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION validate_draft_course_period_scope() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE application enrollment_applications%ROWTYPE;
BEGIN
    SELECT * INTO application FROM enrollment_applications WHERE enrollment_application_id = NEW.enrollment_application_id;
    IF TG_OP = 'UPDATE' AND OLD.enrollment_period_id IS DISTINCT FROM NEW.enrollment_period_id THEN
        RAISE EXCEPTION 'The assigned period is immutable' USING ERRCODE = '23514', CONSTRAINT = 'enrollment_course_period_scope_check';
    END IF;
    IF application.status = 'DRAFT' AND NOT EXISTS (
        SELECT 1 FROM courses course
        JOIN study_plan_spaces space ON space.study_plan_space_id = course.study_plan_space_id
        JOIN enrollment_period_offering_levels selection ON selection.enrollment_period_id = NEW.enrollment_period_id
        JOIN enrollment_periods period ON period.enrollment_period_id = selection.enrollment_period_id
        WHERE course.course_id = NEW.course_id AND course.institution_id = application.institution_id
          AND course.academic_year_id = period.academic_year_id
          AND (application.enrollment_period_id IS NULL OR application.enrollment_period_id = period.enrollment_period_id)
          AND selection.study_plan_id = space.study_plan_id
          AND selection.academic_level_id IS NOT DISTINCT FROM space.academic_level_id
          AND period.scope_configured AND period.deleted_at IS NULL AND period.status = 'OPEN'
          AND period.start_date <= CURRENT_TIMESTAMP AND period.end_date >= CURRENT_TIMESTAMP
    ) THEN
        RAISE EXCEPTION 'Course is outside its open period scope' USING ERRCODE = '23514', CONSTRAINT = 'enrollment_course_period_scope_check';
    END IF;
    INSERT INTO enrollment_application_periods VALUES (NEW.enrollment_application_id, NEW.enrollment_period_id) ON CONFLICT DO NOTHING;
    RETURN NEW;
END $$;

CREATE FUNCTION validate_enrollment_application_academic_year() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.academic_year_id IS NULL THEN
        IF TG_OP = 'INSERT' THEN
            RAISE EXCEPTION 'An academic year is required'
                USING ERRCODE = '23514', CONSTRAINT = 'enrollment_application_academic_year_check';
        END IF;
        IF OLD.academic_year_id IS NOT NULL THEN
            RAISE EXCEPTION 'The academic year cannot be cleared'
                USING ERRCODE = '23514', CONSTRAINT = 'enrollment_application_academic_year_check';
        END IF;
        RETURN NEW;
    END IF;

    PERFORM 1 FROM academic_years
    WHERE academic_year_id = NEW.academic_year_id
      AND institution_id = NEW.institution_id AND deleted_at IS NULL
      AND (TG_OP <> 'INSERT' OR NEW.status <> 'DRAFT' OR status = 'ACTIVE')
    FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Invalid application academic year'
            USING ERRCODE = '23514', CONSTRAINT = 'enrollment_application_academic_year_check';
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF OLD.academic_year_id IS NOT NULL AND OLD.academic_year_id IS DISTINCT FROM NEW.academic_year_id THEN
            RAISE EXCEPTION 'The academic year is immutable'
                USING ERRCODE = '23514', CONSTRAINT = 'enrollment_application_academic_year_check';
        END IF;
    END IF;

    IF EXISTS (
        SELECT 1 FROM enrollment_application_courses selection
        JOIN courses course ON course.course_id = selection.course_id
        WHERE selection.enrollment_application_id = NEW.enrollment_application_id
          AND course.academic_year_id IS DISTINCT FROM NEW.academic_year_id
    ) OR (NEW.enrollment_period_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM enrollment_periods period
        WHERE period.enrollment_period_id = NEW.enrollment_period_id
          AND period.academic_year_id = NEW.academic_year_id
          AND period.institution_id = NEW.institution_id
    )) THEN
        RAISE EXCEPTION 'Application courses or period belong to a different academic year'
            USING ERRCODE = '23514', CONSTRAINT = 'enrollment_application_academic_year_check';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION validate_enrollment_document_gate() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
 IF NEW.status NOT IN ('SUBMITTED','PROVISIONALLY_APPROVED','APPROVED') OR (TG_OP='UPDATE' AND OLD.status=NEW.status) THEN RETURN NEW; END IF;
 IF EXISTS(SELECT 1 FROM enrollment_document_requirements r WHERE r.application_id=NEW.enrollment_application_id AND r.active
 AND ((NEW.status='SUBMITTED' AND r.origin='ORIGINAL' AND r.level='AT_SUBMISSION')
 OR (NEW.status='PROVISIONALLY_APPROVED' AND r.level='AT_SUBMISSION') OR (NEW.status='APPROVED' AND r.level<>'OPTIONAL'))
 AND NOT EXISTS(SELECT 1 FROM enrollment_attachments f WHERE f.requirement_id=r.id AND f.version_status='CURRENT' AND f.deleted_at IS NULL
 AND r.allowed_formats ? f.content_type AND (NEW.status='SUBMITTED' OR f.review_status='ACCEPTED'))) THEN
 RAISE EXCEPTION 'Required documentation incomplete' USING ERRCODE='23514',CONSTRAINT='enrollment_document_gate_check'; END IF;
 RETURN NEW;
END $$;

CREATE FUNCTION validate_enrollment_period_offering() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE period enrollment_periods%ROWTYPE; plan study_plans%ROWTYPE;
BEGIN
    SELECT * INTO period FROM enrollment_periods WHERE enrollment_period_id = NEW.enrollment_period_id FOR UPDATE;
    SELECT * INTO plan FROM study_plans WHERE study_plan_id = NEW.study_plan_id;
    IF plan.institution_id IS DISTINCT FROM period.institution_id OR plan.status = 'DRAFT' OR plan.deleted_at IS NOT NULL THEN
        RAISE EXCEPTION 'Invalid period offering' USING ERRCODE = '23514', CONSTRAINT = 'enrollment_period_scope_invalid';
    END IF;
    IF TG_OP = 'UPDATE' AND (OLD.enrollment_period_id <> NEW.enrollment_period_id OR OLD.study_plan_id <> NEW.study_plan_id) THEN
        RAISE EXCEPTION 'Offering context is immutable' USING ERRCODE = '23514', CONSTRAINT = 'enrollment_period_scope_invalid';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION validate_individual_slot_boundaries() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE schedule_start time; schedule_end time; period_minutes integer;
BEGIN
    SELECT schedule.start_time, schedule.end_time, day.period_duration_minutes
    INTO schedule_start, schedule_end, period_minutes
    FROM course_class_schedules schedule
    JOIN course_class_days day USING (course_class_day_id)
    WHERE schedule.course_class_schedule_id = NEW.course_class_schedule_id;

    IF NOT FOUND OR period_minutes IS NULL OR period_minutes <= 0
       OR NEW.start_time < schedule_start OR NEW.end_time > schedule_end
       OR EXTRACT(EPOCH FROM (NEW.end_time - NEW.start_time)) <> period_minutes * 60
       OR MOD(EXTRACT(EPOCH FROM (NEW.start_time - schedule_start)), NULLIF(period_minutes * 60, 0)) <> 0 THEN
        RAISE EXCEPTION 'Individual slot does not match its schedule'
            USING ERRCODE = '23514', CONSTRAINT = 'course_individual_slots_schedule_bounds_check';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION validate_person_role_assignment_scope() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    assignment_id uuid;
    assignment_scope varchar;
    tenant_id uuid;
    path_count bigint;
BEGIN
    IF TG_OP = 'DELETE' THEN
        assignment_id := OLD.person_role_assignment_id;
    ELSE
        assignment_id := NEW.person_role_assignment_id;
    END IF;
    SELECT access_scope, institution_id INTO assignment_scope, tenant_id
    FROM person_role_assignments WHERE person_role_assignment_id = assignment_id FOR UPDATE;
    IF NOT FOUND THEN RETURN NULL; END IF;
    SELECT count(*) INTO path_count FROM person_role_assignment_training_paths
    WHERE person_role_assignment_id = assignment_id;
    IF (assignment_scope = 'INSTITUTION' AND path_count <> 0)
        OR (assignment_scope = 'TRAINING_PATHS' AND path_count = 0) THEN
        RAISE EXCEPTION 'Invalid role assignment scope' USING ERRCODE = '23514',
            CONSTRAINT = 'person_role_assignment_scope_consistency';
    END IF;
    IF EXISTS (
        SELECT 1 FROM person_role_assignment_training_paths selected
        JOIN training_paths path USING (training_path_id)
        WHERE selected.person_role_assignment_id = assignment_id AND path.institution_id <> tenant_id
    ) THEN
        RAISE EXCEPTION 'Training path belongs to another institution' USING ERRCODE = '23514',
            CONSTRAINT = 'person_role_assignment_scope_consistency';
    END IF;
    RETURN NULL;
END;
$$;

CREATE TABLE academic_enrollment_exceptions (
    exception_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    person_id uuid NOT NULL,
    course_id uuid NOT NULL,
    prerequisite_id uuid NOT NULL,
    required_study_plan_space_id uuid CONSTRAINT academic_enrollment_excepti_required_study_plan_space__not_null NOT NULL,
    required_condition character varying(20) NOT NULL,
    reason character varying(2000) NOT NULL,
    authority_person_id uuid,
    authority_platform_account_id uuid,
    authority_name character varying(300) NOT NULL,
    authorized_at timestamp with time zone NOT NULL,
    CONSTRAINT academic_enrollment_exceptions_reason_check CHECK ((length(TRIM(BOTH FROM reason)) > 0)),
    CONSTRAINT academic_enrollment_exceptions_required_condition_check CHECK (((required_condition)::text = ANY ((ARRAY['REGULAR'::character varying, 'PASSED'::character varying])::text[]))),
    CONSTRAINT academic_exception_authority_check CHECK (((authority_person_id IS NULL) <> (authority_platform_account_id IS NULL)))
);

CREATE TABLE academic_levels (
    academic_level_id uuid NOT NULL,
    study_plan_id uuid NOT NULL,
    name character varying(150) NOT NULL,
    display_order integer NOT NULL,
    description character varying(1000),
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT academic_levels_display_order_check CHECK ((display_order > 0)),
    CONSTRAINT academic_levels_name_format_check CHECK (((name)::text = regexp_replace(btrim((name)::text), '[[:space:]]+'::text, ' '::text, 'g'::text)))
);

CREATE TABLE academic_lifecycle_events (
    academic_lifecycle_event_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    resource_type character varying(30) NOT NULL,
    resource_id uuid NOT NULL,
    action character varying(20) NOT NULL,
    actor_type character varying(20) NOT NULL,
    actor_id uuid NOT NULL,
    reason character varying(500),
    request_id character varying(36),
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT academic_lifecycle_events_action_check CHECK (((action)::text = ANY ((ARRAY['DELETE'::character varying, 'RESTORE'::character varying, 'CREATE_VERSION'::character varying])::text[]))),
    CONSTRAINT academic_lifecycle_events_actor_type_check CHECK (((actor_type)::text = ANY ((ARRAY['INSTITUTION'::character varying, 'PLATFORM'::character varying])::text[]))),
    CONSTRAINT academic_lifecycle_events_resource_type_check CHECK (((resource_type)::text = ANY ((ARRAY['ACADEMIC_YEAR'::character varying, 'TRAINING_PATH'::character varying, 'STUDY_PLAN'::character varying, 'ACADEMIC_SPACE'::character varying, 'INSTRUMENT'::character varying, 'SHIFT'::character varying, 'COURSE'::character varying])::text[])))
);

CREATE TABLE academic_spaces (
    academic_space_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    name character varying(150) NOT NULL,
    description character varying(1000),
    type character varying(20) NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    format character varying(20) NOT NULL,
    instrumental boolean DEFAULT false NOT NULL,
    CONSTRAINT academic_spaces_deleted_state_check CHECK (((deleted_at IS NULL) OR (active = false))),
    CONSTRAINT academic_spaces_format_check CHECK (((format)::text = ANY ((ARRAY['INDIVIDUAL'::character varying, 'GRUPAL'::character varying])::text[]))),
    CONSTRAINT academic_spaces_name_format_check CHECK (((name)::text = regexp_replace(btrim((name)::text), '[[:space:]]+'::text, ' '::text, 'g'::text))),
    CONSTRAINT academic_spaces_type_check CHECK (((type)::text = ANY ((ARRAY['SUBJECT'::character varying, 'WORKSHOP'::character varying, 'SEMINAR'::character varying, 'PRACTICE'::character varying, 'OTHER'::character varying])::text[])))
);

CREATE TABLE academic_years (
    academic_year_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    year integer NOT NULL,
    start_date date,
    end_date date,
    status character varying(20) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    CONSTRAINT academic_years_active_dates_check CHECK ((((status)::text = 'PLANNED'::text) OR ((start_date IS NOT NULL) AND (end_date IS NOT NULL)))),
    CONSTRAINT academic_years_dates_check CHECK (((start_date IS NULL) OR (end_date IS NULL) OR (start_date <= end_date))),
    CONSTRAINT academic_years_dates_pair_check CHECK (((start_date IS NULL) = (end_date IS NULL))),
    CONSTRAINT academic_years_deleted_state_check CHECK (((deleted_at IS NULL) OR ((status)::text = 'PLANNED'::text))),
    CONSTRAINT academic_years_status_check CHECK (((status)::text = ANY ((ARRAY['PLANNED'::character varying, 'ACTIVE'::character varying, 'CLOSED'::character varying])::text[]))),
    CONSTRAINT academic_years_year_check CHECK ((year > 0))
);

CREATE TABLE account_email_change_tokens (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    actor_person_id uuid,
    actor_platform_account_id uuid,
    previous_email character varying(150) NOT NULL,
    new_email character varying(150) NOT NULL,
    token_hash character varying(64) NOT NULL,
    issued_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    CONSTRAINT account_email_change_actor_check CHECK ((num_nonnulls(actor_person_id, actor_platform_account_id) = 1))
);

CREATE TABLE addresses (
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    address_id uuid NOT NULL,
    city_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    apartment character varying(50),
    floor character varying(50),
    number character varying(50),
    additional_info character varying(255),
    neighborhood character varying(255),
    street character varying(255) NOT NULL
);

CREATE TABLE applicant_education_backgrounds (
    applicant_education_background_id uuid CONSTRAINT applicant_education_backgro_applicant_education_backgr_not_null NOT NULL,
    enrollment_application_id uuid CONSTRAINT applicant_education_backgrou_enrollment_application_id_not_null NOT NULL,
    secondary_school character varying(255),
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    school_origin character varying(150),
    current_grade_year character varying(50),
    secondary_completed boolean,
    secondary_degree_title character varying(150),
    currently_studying boolean,
    education_level character varying(30),
    level_completed boolean,
    CONSTRAINT applicant_education_background_education_level_check CHECK (((education_level IS NULL) OR ((education_level)::text = ANY ((ARRAY['NO_SCHOOLING'::character varying, 'INITIAL'::character varying, 'PRIMARY'::character varying, 'SECONDARY'::character varying, 'NON_UNIVERSITY_HIGHER'::character varying, 'UNIVERSITY'::character varying])::text[]))))
);

CREATE TABLE applicant_health_inclusions (
    applicant_health_inclusion_id uuid CONSTRAINT applicant_health_inclusions_applicant_health_inclusion_not_null NOT NULL,
    enrollment_application_id uuid NOT NULL,
    receives_reasonable_adjustments boolean DEFAULT false CONSTRAINT applicant_health_inclusions_receives_reasonable_adjust_not_null NOT NULL,
    adjustment_details text,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone
);

CREATE TABLE applicant_preferences (
    applicant_preference_id uuid NOT NULL,
    enrollment_application_id uuid NOT NULL,
    preferred_shift character varying(150),
    allows_image_use boolean DEFAULT false NOT NULL,
    is_reenrolling boolean DEFAULT false NOT NULL,
    previous_teacher character varying(150),
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone
);

CREATE TABLE applicant_responsibles (
    applicant_responsible_id uuid NOT NULL,
    enrollment_application_id uuid NOT NULL,
    full_name character varying(200),
    document_number character varying(20),
    occupation character varying(100),
    phone_number character varying(50),
    email character varying(150),
    education_level character varying(50),
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone
);

CREATE TABLE audit_events (
    audit_event_id uuid NOT NULL,
    institution_id uuid,
    actor_person_id uuid,
    subject_person_id uuid,
    action character varying(60) NOT NULL,
    entity_type character varying(40) NOT NULL,
    entity_id uuid NOT NULL,
    acted_on_behalf boolean NOT NULL,
    request_id character varying(100),
    occurred_at timestamp(6) with time zone NOT NULL,
    actor_kind character varying(20) DEFAULT 'INSTITUTION'::character varying NOT NULL,
    actor_platform_account_id uuid,
    origin character varying(40),
    details jsonb DEFAULT '{}'::jsonb NOT NULL,
    CONSTRAINT audit_events_actor_shape CHECK (((((actor_kind)::text = 'INSTITUTION'::text) AND (institution_id IS NOT NULL) AND (actor_person_id IS NOT NULL) AND (actor_platform_account_id IS NULL)) OR (((actor_kind)::text = 'PLATFORM'::text) AND (actor_person_id IS NULL) AND (actor_platform_account_id IS NOT NULL)) OR (((actor_kind)::text = 'ANONYMOUS'::text) AND (actor_person_id IS NULL) AND (actor_platform_account_id IS NULL)))),
    CONSTRAINT audit_events_details_object CHECK ((jsonb_typeof(details) = 'object'::text)),
    CONSTRAINT audit_events_on_behalf_consistent CHECK (((NOT acted_on_behalf) OR (((actor_kind)::text = 'INSTITUTION'::text) AND (subject_person_id IS NOT NULL) AND (actor_person_id <> subject_person_id))))
);

CREATE TABLE cities (
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    city_id uuid NOT NULL,
    province_id uuid NOT NULL,
    department_georef_id character varying(20),
    georef_id character varying(20),
    municipality_georef_id character varying(20),
    department_name character varying(255),
    municipality_name character varying(255),
    name character varying(255) NOT NULL
);

CREATE TABLE countries (
    iso_code character varying(3),
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    country_id uuid NOT NULL,
    name character varying(255) NOT NULL
);

CREATE TABLE course_class_days (
    course_class_day_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    course_class_id uuid NOT NULL,
    day_of_week character varying(20) NOT NULL,
    capacity integer,
    period_duration_minutes integer,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT course_class_days_capacity_check CHECK (((capacity IS NULL) OR (capacity > 0))),
    CONSTRAINT course_class_days_day_of_week_check CHECK (((day_of_week)::text = ANY ((ARRAY['MONDAY'::character varying, 'TUESDAY'::character varying, 'WEDNESDAY'::character varying, 'THURSDAY'::character varying, 'FRIDAY'::character varying])::text[]))),
    CONSTRAINT course_class_days_period_duration_check CHECK (((period_duration_minutes IS NULL) OR (period_duration_minutes > 0)))
);

CREATE TABLE course_class_schedules (
    course_class_schedule_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    course_class_day_id uuid NOT NULL,
    start_time time without time zone NOT NULL,
    end_time time without time zone NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT course_class_schedules_time_range_check CHECK ((start_time < end_time))
);

CREATE TABLE course_class_teachers (
    course_class_teacher_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    course_class_id uuid NOT NULL,
    person_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL
);

CREATE TABLE course_classes (
    course_class_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    course_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    class_number integer NOT NULL,
    CONSTRAINT course_classes_class_number_positive CHECK ((class_number > 0))
);

CREATE TABLE course_enrollment_histories (
    course_enrollment_history_id uuid CONSTRAINT course_enrollment_histories_course_enrollment_history__not_null NOT NULL,
    institution_id uuid NOT NULL,
    course_enrollment_id uuid NOT NULL,
    previous_status character varying(40),
    new_status character varying(40),
    previous_academic_status character varying(30),
    new_academic_status character varying(30),
    operation character varying(80) NOT NULL,
    reason text,
    authority_person_id uuid,
    changed_at timestamp(6) with time zone NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL
);

CREATE TABLE course_enrollment_schedules (
    course_enrollment_schedule_id uuid CONSTRAINT course_enrollment_schedules_course_enrollment_schedule_not_null NOT NULL,
    institution_id uuid NOT NULL,
    course_enrollment_id uuid NOT NULL,
    course_class_schedule_id uuid NOT NULL,
    course_individual_slot_id uuid,
    day_of_week character varying(20) NOT NULL,
    start_time time without time zone NOT NULL,
    end_time time without time zone NOT NULL,
    released_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT course_enrollment_schedules_time_range_check CHECK ((start_time < end_time))
);

CREATE TABLE course_enrollments (
    course_enrollment_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    student_id uuid NOT NULL,
    course_id uuid NOT NULL,
    course_class_id uuid NOT NULL,
    source character varying(20) NOT NULL,
    enrollment_application_id uuid,
    status character varying(40) NOT NULL,
    academic_status character varying(30) NOT NULL,
    enrolled_at timestamp(6) with time zone NOT NULL,
    completed_at timestamp(6) with time zone,
    withdrawn_at timestamp(6) with time zone,
    action_authority_person_id uuid,
    action_reason text,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    enrollment_application_course_id uuid,
    CONSTRAINT course_enrollments_academic_status_check CHECK (((academic_status)::text = ANY ((ARRAY['IN_PROGRESS'::character varying, 'PENDING_RESULT'::character varying, 'REGULARIZED'::character varying, 'PROMOTED'::character varying, 'PASSED'::character varying, 'FAILED'::character varying, 'NOT_APPLICABLE'::character varying])::text[]))),
    CONSTRAINT course_enrollments_completed_result_check CHECK ((((status)::text <> 'COMPLETED'::text) OR ((academic_status)::text <> 'IN_PROGRESS'::text))),
    CONSTRAINT course_enrollments_source_application_check CHECK ((((source)::text = 'APPLICATION'::text) = (enrollment_application_id IS NOT NULL))),
    CONSTRAINT course_enrollments_source_check CHECK (((source)::text = ANY ((ARRAY['APPLICATION'::character varying, 'MANUAL'::character varying])::text[]))),
    CONSTRAINT course_enrollments_source_child_check CHECK ((((source)::text = 'APPLICATION'::text) = (enrollment_application_course_id IS NOT NULL))),
    CONSTRAINT course_enrollments_status_check CHECK (((status)::text = ANY ((ARRAY['ENROLLED'::character varying, 'COMPLETED'::character varying, 'WITHDRAWN'::character varying, 'ADMINISTRATIVELY_WITHDRAWN'::character varying])::text[])))
);

CREATE TABLE course_individual_slots (
    course_individual_slot_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    course_class_schedule_id uuid NOT NULL,
    start_time time without time zone NOT NULL,
    end_time time without time zone NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT course_individual_slots_time_range_check CHECK ((start_time < end_time))
);

CREATE TABLE course_waitlist_sequences (
    course_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    last_number integer DEFAULT 0 NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT course_waitlist_sequences_number_check CHECK ((last_number >= 0))
);

CREATE TABLE courses (
    course_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    study_plan_id uuid,
    academic_space_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    status character varying(20) NOT NULL,
    study_plan_space_id uuid NOT NULL,
    training_path_id uuid NOT NULL,
    academic_level_id uuid,
    instrument_id uuid,
    CONSTRAINT courses_deleted_state_check CHECK (((deleted_at IS NULL) OR ((status)::text = 'INACTIVE'::text))),
    CONSTRAINT courses_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'CLOSED'::character varying])::text[])))
);

CREATE TABLE document_definitions (
    id uuid NOT NULL,
    institution_id uuid NOT NULL,
    name character varying(150) NOT NULL,
    instructions character varying(1000) NOT NULL,
    allowed_formats jsonb NOT NULL,
    active boolean NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    used_at timestamp with time zone,
    CONSTRAINT document_definitions_allowed_formats_check CHECK (((jsonb_typeof(allowed_formats) = 'array'::text) AND (jsonb_array_length(allowed_formats) > 0) AND (allowed_formats <@ '["application/pdf", "image/jpeg", "image/png", "image/webp"]'::jsonb))),
    CONSTRAINT document_definitions_name_check CHECK ((length(TRIM(BOTH FROM name)) > 0))
);

CREATE TABLE enrollment_admission_history (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    status character varying(40) NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    actor_id uuid,
    account_type character varying(20) NOT NULL
);

CREATE TABLE enrollment_application_course_assignment_snapshots (
    enrollment_application_course_assignment_snapshot_id uuid CONSTRAINT enrollment_application_cou_enrollment_application_cou_not_null1 NOT NULL,
    institution_id uuid CONSTRAINT enrollment_application_course_assignmen_institution_id_not_null NOT NULL,
    enrollment_application_course_id uuid CONSTRAINT enrollment_application_cou_enrollment_application_cou_not_null2 NOT NULL,
    course_id uuid CONSTRAINT enrollment_application_course_assignment_sna_course_id_not_null NOT NULL,
    course_class_id uuid CONSTRAINT enrollment_application_course_assignme_course_class_id_not_null NOT NULL,
    course_class_schedule_id uuid CONSTRAINT enrollment_application_course_course_class_schedule_id_not_null NOT NULL,
    course_individual_slot_id uuid,
    course_name character varying(255) CONSTRAINT enrollment_application_course_assignment_s_course_name_not_null NOT NULL,
    training_path_name character varying(150) CONSTRAINT enrollment_application_course_assig_training_path_name_not_null NOT NULL,
    study_plan_name character varying(150),
    academic_space_name character varying(150) CONSTRAINT enrollment_application_course_assi_academic_space_name_not_null NOT NULL,
    academic_level_name character varying(150),
    instrument_name character varying(150),
    day_of_week character varying(20) CONSTRAINT enrollment_application_course_assignment_s_day_of_week_not_null NOT NULL,
    start_time time without time zone CONSTRAINT enrollment_application_course_assignment_sn_start_time_not_null NOT NULL,
    end_time time without time zone CONSTRAINT enrollment_application_course_assignment_snap_end_time_not_null NOT NULL,
    assigned_at timestamp(6) with time zone CONSTRAINT enrollment_application_course_assignment_s_assigned_at_not_null NOT NULL,
    assigned_by_person_id uuid,
    operation character varying(80) CONSTRAINT enrollment_application_course_assignment_sna_operation_not_null NOT NULL,
    created_at timestamp(6) with time zone CONSTRAINT enrollment_application_course_assignment_sn_created_at_not_null NOT NULL,
    updated_at timestamp(6) with time zone CONSTRAINT enrollment_application_course_assignment_sn_updated_at_not_null NOT NULL
);

CREATE TABLE enrollment_application_courses (
    enrollment_application_course_id uuid CONSTRAINT enrollment_application_cour_enrollment_application_cou_not_null NOT NULL,
    institution_id uuid NOT NULL,
    enrollment_application_id uuid CONSTRAINT enrollment_application_cours_enrollment_application_id_not_null NOT NULL,
    course_id uuid NOT NULL,
    preferred_teacher_id uuid,
    status character varying(20) NOT NULL,
    requested_at timestamp(6) with time zone,
    submitted_with_capacity boolean,
    waitlist_number integer,
    waitlisted_at timestamp(6) with time zone,
    waitlist_reason character varying(80),
    resolved_at timestamp(6) with time zone,
    resolved_by_person_id uuid,
    resolution_reason_code character varying(80),
    resolution_reason_text text,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    enrollment_period_id uuid NOT NULL,
    CONSTRAINT enrollment_application_courses_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'WAITLISTED'::character varying, 'ENROLLED'::character varying, 'REJECTED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT enrollment_application_courses_waitlist_complete CHECK ((((status)::text <> 'WAITLISTED'::text) OR ((waitlist_number IS NOT NULL) AND (waitlisted_at IS NOT NULL) AND (waitlist_reason IS NOT NULL)))),
    CONSTRAINT enrollment_application_courses_waitlist_number_positive CHECK (((waitlist_number IS NULL) OR (waitlist_number > 0)))
);

CREATE TABLE enrollment_application_periods (
    enrollment_application_id uuid CONSTRAINT enrollment_application_perio_enrollment_application_id_not_null NOT NULL,
    enrollment_period_id uuid NOT NULL
);

CREATE TABLE enrollment_applications (
    enrollment_application_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    applicant_person_id uuid NOT NULL,
    academic_year_id uuid,
    enrollment_period_id uuid,
    status character varying(40) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    rejection_reason text,
    resolved_at timestamp with time zone,
    resolved_by_person_id uuid,
    training_path_id uuid NOT NULL,
    submitted_by_person_id uuid,
    form_version_id uuid,
    custom_answers jsonb DEFAULT '{}'::jsonb NOT NULL,
    draft_revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT enrollment_application_base_answers_check CHECK (((form_version_id IS NOT NULL) OR (custom_answers = '{}'::jsonb))),
    CONSTRAINT enrollment_application_custom_answers_check CHECK ((jsonb_typeof(custom_answers) = 'object'::text)),
    CONSTRAINT enrollment_application_draft_revision_check CHECK ((draft_revision >= 0)),
    CONSTRAINT enrollment_applications_resolution_consistency_check CHECK ((((status)::text = 'REJECTED'::text) = (rejection_reason IS NOT NULL))),
    CONSTRAINT enrollment_applications_status_check CHECK (((status)::text = ANY (ARRAY[('DRAFT'::character varying)::text, ('SUBMITTED'::character varying)::text, ('PROVISIONALLY_APPROVED'::character varying)::text, ('APPROVED'::character varying)::text, ('REJECTED'::character varying)::text, ('CANCELLED'::character varying)::text])))
);

CREATE TABLE enrollment_attachments (
    enrollment_attachment_id uuid NOT NULL,
    enrollment_application_id uuid NOT NULL,
    original_file_name character varying(255) NOT NULL,
    storage_path character varying(500) NOT NULL,
    content_type character varying(100) NOT NULL,
    file_size bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    requirement_id uuid NOT NULL,
    version_status character varying(20) DEFAULT 'CURRENT'::character varying NOT NULL,
    review_status character varying(20) DEFAULT 'PENDING_REVIEW'::character varying NOT NULL,
    uploaded_by uuid,
    uploader_type character varying(20) NOT NULL,
    reviewed_by uuid,
    reviewer_type character varying(20),
    reviewed_at timestamp with time zone,
    observation character varying(2000),
    CONSTRAINT enrollment_attachment_observation_check CHECK ((((review_status)::text <> 'OBSERVED'::text) OR ((length(TRIM(BOTH FROM observation)) > 0) AND (observation IS NOT NULL)))),
    CONSTRAINT enrollment_attachment_review_check CHECK (((((review_status)::text = 'PENDING_REVIEW'::text) AND (reviewed_at IS NULL) AND (reviewer_type IS NULL)) OR (((review_status)::text = ANY ((ARRAY['ACCEPTED'::character varying, 'OBSERVED'::character varying])::text[])) AND (reviewed_at IS NOT NULL) AND (reviewer_type IS NOT NULL)))),
    CONSTRAINT enrollment_attachments_review_status_check CHECK (((review_status)::text = ANY ((ARRAY['PENDING_REVIEW'::character varying, 'OBSERVED'::character varying, 'ACCEPTED'::character varying])::text[]))),
    CONSTRAINT enrollment_attachments_version_status_check CHECK (((version_status)::text = ANY ((ARRAY['CURRENT'::character varying, 'SUPERSEDED'::character varying, 'WITHDRAWN'::character varying])::text[])))
);

CREATE TABLE enrollment_document_audit (
    id uuid NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    actor_id uuid,
    account_type character varying(20) NOT NULL,
    institution_id uuid,
    application_id uuid,
    attachment_id uuid,
    action character varying(40) NOT NULL,
    result character varying(20) NOT NULL,
    request_id character varying(36),
    CONSTRAINT enrollment_document_audit_account_type_check CHECK (((account_type)::text = ANY ((ARRAY['INSTITUTION'::character varying, 'PLATFORM'::character varying, 'SYSTEM'::character varying, 'ANONYMOUS'::character varying])::text[]))),
    CONSTRAINT enrollment_document_audit_action_check CHECK (((action)::text = ANY ((ARRAY['METADATA_READ'::character varying, 'CONTENT_ACCESS'::character varying, 'UPLOAD'::character varying, 'REPLACE'::character varying, 'DELETE'::character varying, 'PHYSICAL_DELETE'::character varying, 'REVIEW'::character varying, 'PROVISIONAL_APPROVAL'::character varying, 'FINAL_APPROVAL'::character varying])::text[]))),
    CONSTRAINT enrollment_document_audit_result_check CHECK (((result)::text = ANY ((ARRAY['ALLOWED'::character varying, 'DENIED'::character varying, 'SUCCESS'::character varying, 'RETRY'::character varying])::text[])))
);

CREATE TABLE enrollment_document_requests (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    reason character varying(2000) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    actor_id uuid NOT NULL,
    account_type character varying(20) NOT NULL,
    actor_name character varying(300) NOT NULL,
    CONSTRAINT enrollment_document_requests_account_type_check CHECK (((account_type)::text = ANY ((ARRAY['INSTITUTION'::character varying, 'PLATFORM'::character varying])::text[]))),
    CONSTRAINT enrollment_document_requests_reason_check CHECK ((length(TRIM(BOTH FROM reason)) > 0))
);

CREATE TABLE enrollment_document_requirements (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    source_requirement_id uuid,
    name character varying(150) NOT NULL,
    instructions character varying(1000) NOT NULL,
    level character varying(40) NOT NULL,
    allowed_formats jsonb NOT NULL,
    display_order integer NOT NULL,
    document_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    definition_revision bigint,
    assignment_revision bigint,
    specific_instructions character varying(1000),
    active boolean DEFAULT true NOT NULL,
    origin character varying(20) DEFAULT 'ORIGINAL'::character varying NOT NULL,
    request_id uuid,
    CONSTRAINT enrollment_document_requirements_allowed_formats_check CHECK (((jsonb_typeof(allowed_formats) = 'array'::text) AND (jsonb_array_length(allowed_formats) > 0) AND (allowed_formats <@ '["application/pdf", "image/jpeg", "image/png", "image/webp"]'::jsonb))),
    CONSTRAINT enrollment_document_requirements_display_order_check CHECK ((display_order >= 0)),
    CONSTRAINT enrollment_document_requirements_level_check CHECK (((level)::text = ANY ((ARRAY['AT_SUBMISSION'::character varying, 'BEFORE_CONFIRMATION'::character varying, 'OPTIONAL'::character varying])::text[]))),
    CONSTRAINT enrollment_requirement_origin_check CHECK (((((origin)::text = 'ORIGINAL'::text) AND (source_requirement_id IS NOT NULL) AND (request_id IS NULL)) OR (((origin)::text = 'ADDITIONAL'::text) AND (source_requirement_id IS NULL) AND (request_id IS NOT NULL))))
);

CREATE TABLE enrollment_form_assignments (
    id uuid NOT NULL,
    institution_id uuid NOT NULL,
    training_path_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    form_version_id uuid NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT enrollment_form_assignments_revision_check CHECK ((revision >= 0))
);

CREATE TABLE enrollment_form_versions (
    id uuid NOT NULL,
    institution_id uuid NOT NULL,
    form_id uuid NOT NULL,
    version_number integer NOT NULL,
    status character varying(20) NOT NULL,
    definition jsonb NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    published_at timestamp with time zone,
    archived_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT enrollment_form_versions_definition_check CHECK ((jsonb_typeof(definition) = 'object'::text)),
    CONSTRAINT enrollment_form_versions_number_check CHECK ((version_number > 0)),
    CONSTRAINT enrollment_form_versions_revision_check CHECK ((revision >= 0)),
    CONSTRAINT enrollment_form_versions_state_check CHECK (((((status)::text = 'DRAFT'::text) AND (published_at IS NULL) AND (archived_at IS NULL)) OR (((status)::text = 'PUBLISHED'::text) AND (published_at IS NOT NULL) AND (archived_at IS NULL)) OR (((status)::text = 'ARCHIVED'::text) AND (published_at IS NOT NULL) AND (archived_at IS NOT NULL) AND (archived_at >= published_at))))
);

CREATE TABLE enrollment_forms (
    id uuid NOT NULL,
    institution_id uuid NOT NULL,
    name character varying(150) NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT enrollment_forms_name_check CHECK (((name)::text ~ '[^[:space:]]'::text)),
    CONSTRAINT enrollment_forms_revision_check CHECK ((revision >= 0))
);

CREATE TABLE enrollment_pdf_settings (
    institution_id uuid NOT NULL,
    title text NOT NULL,
    header_text text,
    introduction_text text,
    footer_text text,
    show_applicant_signature boolean NOT NULL,
    show_responsible_signature boolean NOT NULL,
    CONSTRAINT enrollment_pdf_settings_footer_check CHECK (((footer_text IS NULL) OR (length(footer_text) <= 500))),
    CONSTRAINT enrollment_pdf_settings_header_check CHECK (((header_text IS NULL) OR (length(header_text) <= 500))),
    CONSTRAINT enrollment_pdf_settings_introduction_check CHECK (((introduction_text IS NULL) OR (length(introduction_text) <= 1000))),
    CONSTRAINT enrollment_pdf_settings_title_check CHECK ((((length(title) >= 1) AND (length(title) <= 150)) AND (title ~ '[^[:space:]]'::text)))
);

CREATE TABLE enrollment_period_offering_levels (
    offering_level_id uuid NOT NULL,
    offering_id uuid NOT NULL,
    academic_level_id uuid,
    study_plan_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    enrollment_period_id uuid NOT NULL,
    availability tstzrange NOT NULL,
    reserves boolean NOT NULL
);

CREATE TABLE enrollment_period_offerings (
    offering_id uuid NOT NULL,
    enrollment_period_id uuid NOT NULL,
    study_plan_id uuid NOT NULL
);

CREATE TABLE enrollment_periods (
    enrollment_period_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    name character varying(150) NOT NULL,
    start_date timestamp with time zone NOT NULL,
    end_date timestamp with time zone NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    scope_configured boolean DEFAULT false NOT NULL,
    CONSTRAINT enrollment_periods_dates_check CHECK ((start_date <= end_date)),
    CONSTRAINT enrollment_periods_status_check CHECK (((status)::text = ANY ((ARRAY['PLANNED'::character varying, 'OPEN'::character varying, 'CLOSED'::character varying])::text[])))
);

CREATE TABLE enrollment_requirement_changes (
    id uuid NOT NULL,
    requirement_id uuid NOT NULL,
    action character varying(20) NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    actor_id uuid,
    account_type character varying(20) NOT NULL,
    name character varying(150) NOT NULL,
    CONSTRAINT enrollment_requirement_changes_action_check CHECK (((action)::text = ANY ((ARRAY['ADDED'::character varying, 'UPDATED'::character varying, 'RETIRED'::character varying, 'REACTIVATED'::character varying])::text[])))
);

CREATE TABLE enrollment_storage_jobs (
    id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    state character varying(12) NOT NULL,
    provider character varying(10) NOT NULL,
    location character varying(1024) NOT NULL,
    prefix character varying(500) NOT NULL,
    storage_path character varying(500) NOT NULL,
    institution_id uuid NOT NULL,
    application_id uuid NOT NULL,
    attachment_id uuid,
    actor_id uuid,
    account_type character varying(20) NOT NULL,
    request_id character varying(36),
    attempts integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone,
    last_error character varying(100),
    CONSTRAINT enrollment_storage_jobs_attempts_check CHECK ((attempts >= 0)),
    CONSTRAINT enrollment_storage_jobs_check CHECK ((((state)::text = 'PENDING'::text) = (next_attempt_at IS NOT NULL))),
    CONSTRAINT enrollment_storage_jobs_check1 CHECK ((((state)::text <> 'ACTIVE'::text) OR (attachment_id IS NOT NULL))),
    CONSTRAINT enrollment_storage_jobs_provider_check CHECK (((provider)::text = ANY ((ARRAY['local'::character varying, 's3'::character varying])::text[]))),
    CONSTRAINT enrollment_storage_jobs_state_check CHECK (((state)::text = ANY ((ARRAY['PENDING'::character varying, 'ACTIVE'::character varying, 'DONE'::character varying])::text[])))
);

CREATE TABLE institution_enrollment_locks (
    institution_id uuid NOT NULL,
    created_at timestamp(6) with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE institutional_email_verification_tokens (
    email_verification_token_id uuid CONSTRAINT institutional_email_verific_email_verification_token_i_not_null NOT NULL,
    user_id uuid NOT NULL,
    token_hash character varying(64) NOT NULL,
    recipient_email character varying(150) CONSTRAINT institutional_email_verification_token_recipient_email_not_null NOT NULL,
    issued_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    CONSTRAINT email_verification_expiration CHECK ((expires_at > issued_at))
);

CREATE TABLE institutional_password_reset_tokens (
    password_reset_token_id uuid CONSTRAINT institutional_password_reset_t_password_reset_token_id_not_null NOT NULL,
    user_id uuid NOT NULL,
    token_hash character varying(64) NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    used_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL
);

CREATE TABLE institutions (
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    city_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    phone_number character varying(30),
    number character varying(50),
    email character varying(150),
    additional_info character varying(255),
    name character varying(255) NOT NULL,
    neighborhood character varying(255),
    street character varying(255),
    public_subdomain character varying(63),
    logo_key character varying(255),
    logo_content_type character varying(32),
    logo_version character varying(36),
    logo_size bigint,
    short_name character varying(50),
    CONSTRAINT institutions_logo_metadata_check CHECK ((((logo_key IS NULL) AND (logo_content_type IS NULL) AND (logo_version IS NULL) AND (logo_size IS NULL)) OR ((logo_key IS NOT NULL) AND (logo_content_type IS NOT NULL) AND ((logo_content_type)::text = ANY ((ARRAY['image/png'::character varying, 'image/jpeg'::character varying, 'image/webp'::character varying])::text[])) AND (logo_version IS NOT NULL) AND ((logo_size >= 1) AND (logo_size <= 2097152)) AND (logo_size IS NOT NULL)))),
    CONSTRAINT institutions_public_subdomain_dns_check CHECK (((public_subdomain IS NULL) OR ((public_subdomain)::text ~ '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$'::text))),
    CONSTRAINT institutions_public_subdomain_reserved_check CHECK (((public_subdomain IS NULL) OR ((public_subdomain)::text <> ALL ((ARRAY['www'::character varying, 'api'::character varying, 'admin'::character varying, 'auth'::character varying, 'test'::character varying, 'testing'::character varying, 'qa'::character varying, 'staging'::character varying, 'localhost'::character varying])::text[]))))
);

CREATE TABLE instruments (
    instrument_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    name character varying(150) NOT NULL,
    description character varying(1000),
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    CONSTRAINT instruments_deleted_state_check CHECK (((deleted_at IS NULL) OR (active = false))),
    CONSTRAINT instruments_name_format_check CHECK (((name)::text = regexp_replace(btrim((name)::text), '[[:space:]]+'::text, ' '::text, 'g'::text)))
);

CREATE TABLE passkey_credentials (
    passkey_credential_id uuid NOT NULL,
    user_id uuid NOT NULL,
    credential_id text NOT NULL,
    public_key_cose bytea NOT NULL,
    signature_count bigint DEFAULT 0 NOT NULL,
    transports character varying(255),
    backup_eligible boolean DEFAULT false NOT NULL,
    backup_state boolean DEFAULT false NOT NULL,
    uv_initialized boolean DEFAULT false NOT NULL,
    user_handle bytea NOT NULL,
    attestation_object bytea,
    attestation_client_data_json bytea,
    label character varying(100) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    last_used_at timestamp(6) with time zone,
    revoked_at timestamp(6) with time zone,
    CONSTRAINT passkey_credentials_label_length CHECK (((char_length((label)::text) >= 1) AND (char_length((label)::text) <= 100)))
);

CREATE TABLE people (
    birth_date date,
    deleted boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    document_number character varying(8) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    address_id uuid,
    birth_city_id uuid,
    institution_id uuid NOT NULL,
    nationality_country_id uuid,
    person_id uuid NOT NULL,
    phone_number character varying(30),
    email character varying(150),
    first_name character varying(255) NOT NULL,
    last_name character varying(255) NOT NULL,
    CONSTRAINT people_document_number_format CHECK (((document_number)::text ~ '^[0-9]{8}$'::text)),
    CONSTRAINT people_email_not_blank CHECK (((email IS NULL) OR (btrim((email)::text) <> ''::text))),
    CONSTRAINT people_first_name_length CHECK (((char_length((first_name)::text) >= 3) AND (char_length((first_name)::text) <= 255))),
    CONSTRAINT people_last_name_length CHECK (((char_length((last_name)::text) >= 3) AND (char_length((last_name)::text) <= 255))),
    CONSTRAINT people_nationality_required CHECK ((nationality_country_id IS NOT NULL)),
    CONSTRAINT people_phone_number_required CHECK (((phone_number IS NOT NULL) AND ((phone_number)::text ~ '[^[:space:]]'::text)))
);

CREATE TABLE permissions (
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    permission_id uuid NOT NULL,
    scope character varying(20) NOT NULL,
    code character varying(100) NOT NULL,
    description character varying(255) NOT NULL,
    CONSTRAINT permissions_scope_check CHECK (((scope)::text = ANY (ARRAY[('PLATFORM'::character varying)::text, ('INSTITUTION'::character varying)::text])))
);

CREATE TABLE person_access_invitations (
    invitation_id uuid NOT NULL,
    person_id uuid NOT NULL,
    representation_id uuid,
    token_hash character varying(64) NOT NULL,
    email character varying(150) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    institutional_issuer_person_id uuid,
    issuing_institution_id uuid,
    platform_issuer_account_id uuid,
    purpose character varying(24) DEFAULT 'INITIAL_ACCESS'::character varying NOT NULL,
    issued_at timestamp with time zone NOT NULL,
    cancelled_at timestamp with time zone,
    expected_email character varying(150),
    mail_outcome character varying(16),
    CONSTRAINT person_access_invitation_origin_check CHECK ((((representation_id IS NOT NULL) AND (institutional_issuer_person_id IS NULL) AND (platform_issuer_account_id IS NULL) AND (issuing_institution_id IS NULL) AND ((purpose)::text = 'INITIAL_ACCESS'::text)) OR ((representation_id IS NULL) AND (issuing_institution_id IS NOT NULL) AND (num_nonnulls(institutional_issuer_person_id, platform_issuer_account_id) = 1)))),
    CONSTRAINT person_access_invitation_purpose_check CHECK (((purpose)::text = ANY ((ARRAY['INITIAL_ACCESS'::character varying, 'AUTONOMY'::character varying])::text[]))),
    CONSTRAINT person_access_invitation_resolution_check CHECK (((consumed_at IS NULL) OR (cancelled_at IS NULL)))
);

CREATE TABLE person_guardian_attachments (
    person_guardian_attachment_id uuid CONSTRAINT person_guardian_attachments_person_guardian_attachment_not_null NOT NULL,
    institution_id uuid NOT NULL,
    person_guardian_id uuid NOT NULL,
    original_file_name character varying(255) NOT NULL,
    storage_path character varying(500) NOT NULL,
    content_type character varying(100) NOT NULL,
    file_size bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL
);

CREATE TABLE person_guardians (
    person_guardian_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    tutor_person_id uuid NOT NULL,
    dependent_person_id uuid,
    relationship character varying(30) NOT NULL,
    is_primary_contact boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    resolved_by_person_id uuid,
    resolved_at timestamp(6) with time zone,
    declared_first_name character varying(255),
    declared_last_name character varying(255),
    own_access_requested boolean DEFAULT false NOT NULL,
    declared_email character varying(150),
    declared_birth_date date,
    declared_document_number character varying(8) NOT NULL,
    request_mode character varying(20) DEFAULT 'NEW'::character varying NOT NULL,
    declared_phone_number character varying(30),
    declared_nationality_country_id uuid,
    CONSTRAINT person_guardians_active_subject_check CHECK ((((status)::text <> 'ACTIVE'::text) OR (dependent_person_id IS NOT NULL))),
    CONSTRAINT person_guardians_no_self_guardianship CHECK ((tutor_person_id <> dependent_person_id)),
    CONSTRAINT person_guardians_relationship_check CHECK (((relationship)::text = ANY ((ARRAY['MOTHER'::character varying, 'FATHER'::character varying, 'LEGAL_GUARDIAN'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT person_guardians_request_mode_check CHECK (((request_mode)::text = ANY ((ARRAY['NEW'::character varying, 'EXISTING'::character varying])::text[]))),
    CONSTRAINT person_guardians_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACTIVE'::character varying, 'REJECTED'::character varying, 'ENDED'::character varying])::text[])))
);

CREATE TABLE person_role_assignment_training_paths (
    person_role_assignment_id uuid CONSTRAINT person_role_assignment_train_person_role_assignment_id_not_null NOT NULL,
    training_path_id uuid NOT NULL,
    institution_id uuid NOT NULL
);

CREATE TABLE person_role_assignments (
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    institution_id uuid NOT NULL,
    person_id uuid NOT NULL,
    person_role_assignment_id uuid NOT NULL,
    role_id uuid NOT NULL,
    access_scope character varying(20) DEFAULT 'INSTITUTION'::character varying NOT NULL,
    CONSTRAINT person_role_assignments_access_scope_check CHECK (((access_scope)::text = ANY ((ARRAY['INSTITUTION'::character varying, 'TRAINING_PATHS'::character varying])::text[])))
);

CREATE TABLE platform_account_roles (
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    platform_account_id uuid NOT NULL,
    platform_account_role_id uuid NOT NULL,
    role_id uuid NOT NULL
);

CREATE TABLE platform_accounts (
    enabled boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    platform_account_id uuid NOT NULL,
    password character varying(100) NOT NULL,
    email character varying(150) NOT NULL,
    first_name character varying(255) NOT NULL,
    last_name character varying(255) NOT NULL
);

CREATE TABLE platform_email_change_tokens (
    id uuid NOT NULL,
    platform_account_id uuid NOT NULL,
    previous_email character varying(150) NOT NULL,
    new_email character varying(150) NOT NULL,
    password_fingerprint character varying(64) NOT NULL,
    token_hash character varying(64) NOT NULL,
    issued_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    CONSTRAINT platform_email_change_email_check CHECK (((new_email)::text <> (previous_email)::text)),
    CONSTRAINT platform_email_change_expiry_check CHECK ((expires_at > issued_at))
);

CREATE TABLE platform_passkey_credentials (
    platform_passkey_credential_id uuid CONSTRAINT platform_passkey_credential_platform_passkey_credentia_not_null NOT NULL,
    platform_account_id uuid NOT NULL,
    credential_id text NOT NULL,
    public_key_cose bytea NOT NULL,
    signature_count bigint DEFAULT 0 NOT NULL,
    transports character varying(255),
    backup_eligible boolean DEFAULT false NOT NULL,
    backup_state boolean DEFAULT false NOT NULL,
    uv_initialized boolean DEFAULT false NOT NULL,
    user_handle bytea NOT NULL,
    attestation_object bytea,
    attestation_client_data_json bytea,
    label character varying(100) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    last_used_at timestamp(6) with time zone,
    revoked_at timestamp(6) with time zone,
    CONSTRAINT platform_passkey_credentials_label_length CHECK (((char_length((label)::text) >= 1) AND (char_length((label)::text) <= 100)))
);

CREATE TABLE platform_refresh_tokens (
    revoked boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    platform_account_id uuid NOT NULL,
    platform_refresh_token_id uuid NOT NULL,
    platform_session_id uuid NOT NULL,
    family_id character varying(36) NOT NULL,
    token_hash character varying(255) NOT NULL
);

CREATE TABLE platform_sessions (
    active boolean NOT NULL,
    remember_me boolean NOT NULL,
    ended_at timestamp(6) with time zone,
    started_at timestamp(6) with time zone NOT NULL,
    platform_account_id uuid NOT NULL,
    platform_session_id uuid NOT NULL,
    ip_address character varying(45),
    user_agent character varying(255)
);

CREATE TABLE prerequisites (
    prerequisite_id uuid NOT NULL,
    study_plan_id uuid NOT NULL,
    target_study_plan_space_id uuid NOT NULL,
    required_study_plan_space_id uuid NOT NULL,
    requirement_stage character varying(20) NOT NULL,
    required_condition character varying(20) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT prerequisites_distinct_spaces_check CHECK ((target_study_plan_space_id <> required_study_plan_space_id)),
    CONSTRAINT prerequisites_required_condition_check CHECK (((required_condition)::text = ANY ((ARRAY['REGULAR'::character varying, 'PASSED'::character varying])::text[]))),
    CONSTRAINT prerequisites_requirement_stage_check CHECK (((requirement_stage)::text = ANY ((ARRAY['TO_ENROLL'::character varying, 'TO_PASS'::character varying])::text[])))
);

CREATE TABLE provinces (
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    country_id uuid NOT NULL,
    province_id uuid NOT NULL,
    georef_id character varying(20),
    name character varying(255) NOT NULL
);

CREATE TABLE refresh_tokens (
    revoked boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    refresh_token_id uuid NOT NULL,
    session_id uuid NOT NULL,
    family_id character varying(36) NOT NULL,
    token_hash character varying(255) NOT NULL
);

CREATE TABLE role_permissions (
    permission_id uuid NOT NULL,
    role_id uuid NOT NULL
);

CREATE TABLE roles (
    is_system boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    institution_id uuid,
    role_id uuid NOT NULL,
    scope character varying(20) NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(100) NOT NULL,
    CONSTRAINT roles_scope_check CHECK (((scope)::text = ANY (ARRAY[('PLATFORM'::character varying)::text, ('INSTITUTION'::character varying)::text])))
);

CREATE TABLE shifts (
    shift_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    name character varying(150) NOT NULL,
    description character varying(1000),
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    CONSTRAINT shifts_deleted_state_check CHECK (((deleted_at IS NULL) OR (active = false))),
    CONSTRAINT shifts_name_format_check CHECK (((name)::text = regexp_replace(btrim((name)::text), '[[:space:]]+'::text, ' '::text, 'g'::text)))
);

CREATE SEQUENCE student_file_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE students (
    enrollment_date date NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    institution_id uuid NOT NULL,
    person_id uuid NOT NULL,
    student_id uuid NOT NULL,
    status character varying(30) NOT NULL,
    file_number character varying(50),
    CONSTRAINT students_status_check CHECK (((status)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('INACTIVE'::character varying)::text, ('GRADUATED'::character varying)::text, ('WITHDRAWN'::character varying)::text, ('SUSPENDED'::character varying)::text])))
);

CREATE TABLE study_plan_space_instruments (
    study_plan_space_instrument_id uuid CONSTRAINT study_plan_space_instrument_study_plan_space_instrumen_not_null NOT NULL,
    institution_id uuid NOT NULL,
    study_plan_space_id uuid NOT NULL,
    instrument_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);

CREATE TABLE study_plan_spaces (
    study_plan_space_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    study_plan_id uuid NOT NULL,
    academic_space_id uuid NOT NULL,
    academic_level_id uuid,
    requirement_type character varying(20) NOT NULL,
    display_order integer NOT NULL,
    approval_mode character varying(30) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT study_plan_spaces_approval_mode_check CHECK (((approval_mode)::text = ANY ((ARRAY['PROMOTION'::character varying, 'FINAL_EXAM'::character varying, 'PROMOTION_OR_FINAL_EXAM'::character varying])::text[]))),
    CONSTRAINT study_plan_spaces_display_order_check CHECK ((display_order > 0)),
    CONSTRAINT study_plan_spaces_requirement_type_check CHECK (((requirement_type)::text = ANY ((ARRAY['REQUIRED'::character varying, 'OPTIONAL'::character varying])::text[])))
);

CREATE TABLE study_plans (
    study_plan_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    training_path_id uuid NOT NULL,
    name character varying(150) NOT NULL,
    effective_from date,
    effective_to date,
    status character varying(20) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    previous_version_id uuid,
    version_number integer DEFAULT 1 NOT NULL,
    CONSTRAINT study_plans_active_dates_check CHECK ((((status)::text = 'DRAFT'::text) OR (effective_from IS NOT NULL))),
    CONSTRAINT study_plans_dates_check CHECK (((effective_from IS NULL) OR (effective_to IS NULL) OR (effective_from <= effective_to))),
    CONSTRAINT study_plans_deleted_state_check CHECK (((deleted_at IS NULL) OR ((status)::text = ANY (ARRAY[('DRAFT'::character varying)::text, ('INACTIVE'::character varying)::text])))),
    CONSTRAINT study_plans_end_requires_start_check CHECK (((effective_to IS NULL) OR (effective_from IS NOT NULL))),
    CONSTRAINT study_plans_name_format_check CHECK (((name)::text = regexp_replace(btrim((name)::text), '[[:space:]]+'::text, ' '::text, 'g'::text))),
    CONSTRAINT study_plans_previous_version_self_check CHECK (((previous_version_id IS NULL) OR (previous_version_id <> study_plan_id))),
    CONSTRAINT study_plans_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'INACTIVE'::character varying])::text[]))),
    CONSTRAINT study_plans_version_number_check CHECK ((version_number > 0))
);

CREATE TABLE training_path_document_requirements (
    id uuid NOT NULL,
    training_path_id uuid NOT NULL,
    level character varying(40) NOT NULL,
    display_order integer NOT NULL,
    active boolean NOT NULL,
    document_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    specific_instructions character varying(1000),
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT training_path_document_requirements_display_order_check CHECK ((display_order >= 0)),
    CONSTRAINT training_path_document_requirements_level_check CHECK (((level)::text = ANY ((ARRAY['AT_SUBMISSION'::character varying, 'BEFORE_CONFIRMATION'::character varying, 'OPTIONAL'::character varying])::text[])))
);

CREATE TABLE training_paths (
    training_path_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    name character varying(150) NOT NULL,
    description character varying(1000),
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    CONSTRAINT training_paths_deleted_state_check CHECK (((deleted_at IS NULL) OR (active = false))),
    CONSTRAINT training_paths_name_format_check CHECK (((name)::text = regexp_replace(btrim((name)::text), '[[:space:]]+'::text, ' '::text, 'g'::text)))
);

CREATE TABLE user_sessions (
    active boolean NOT NULL,
    remember_me boolean NOT NULL,
    ended_at timestamp(6) with time zone,
    started_at timestamp(6) with time zone NOT NULL,
    user_id uuid NOT NULL,
    user_session_id uuid NOT NULL,
    ip_address character varying(45),
    user_agent character varying(255),
    active_person_id uuid
);

CREATE TABLE users (
    enabled boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    institution_id uuid NOT NULL,
    person_id uuid NOT NULL,
    user_id uuid NOT NULL,
    password character varying(255) NOT NULL,
    webauthn_user_handle bytea,
    email_verification_status character varying(24) DEFAULT 'NOT_REQUIRED'::character varying NOT NULL,
    email_verified_at timestamp with time zone,
    self_access_enabled boolean DEFAULT true NOT NULL,
    CONSTRAINT users_email_verification_state CHECK (((((email_verification_status)::text = ANY ((ARRAY['NOT_REQUIRED'::character varying, 'PENDING'::character varying])::text[])) AND (email_verified_at IS NULL)) OR (((email_verification_status)::text = 'VERIFIED'::text) AND (email_verified_at IS NOT NULL))))
);

ALTER TABLE ONLY academic_enrollment_exceptions
    ADD CONSTRAINT academic_enrollment_exceptions_pkey PRIMARY KEY (exception_id);

ALTER TABLE ONLY academic_levels
    ADD CONSTRAINT academic_levels_pkey PRIMARY KEY (academic_level_id);

ALTER TABLE ONLY academic_levels
    ADD CONSTRAINT academic_levels_study_plan_id_id_unique UNIQUE (study_plan_id, academic_level_id);

ALTER TABLE ONLY academic_levels
    ADD CONSTRAINT academic_levels_study_plan_order_unique UNIQUE (study_plan_id, display_order);

ALTER TABLE ONLY academic_lifecycle_events
    ADD CONSTRAINT academic_lifecycle_events_pkey PRIMARY KEY (academic_lifecycle_event_id);

ALTER TABLE ONLY academic_spaces
    ADD CONSTRAINT academic_spaces_institution_id_id_unique UNIQUE (institution_id, academic_space_id);

ALTER TABLE ONLY academic_spaces
    ADD CONSTRAINT academic_spaces_pkey PRIMARY KEY (academic_space_id);

ALTER TABLE ONLY academic_years
    ADD CONSTRAINT academic_years_institution_id_id_unique UNIQUE (institution_id, academic_year_id);

ALTER TABLE ONLY academic_years
    ADD CONSTRAINT academic_years_pkey PRIMARY KEY (academic_year_id);

ALTER TABLE ONLY account_email_change_tokens
    ADD CONSTRAINT account_email_change_tokens_pkey PRIMARY KEY (id);

ALTER TABLE ONLY account_email_change_tokens
    ADD CONSTRAINT account_email_change_tokens_token_hash_key UNIQUE (token_hash);

ALTER TABLE ONLY account_email_change_tokens
    ADD CONSTRAINT account_email_change_tokens_user_id_key UNIQUE (user_id);

ALTER TABLE ONLY addresses
    ADD CONSTRAINT addresses_institution_id_id_unique UNIQUE (institution_id, address_id);

ALTER TABLE ONLY addresses
    ADD CONSTRAINT addresses_pkey PRIMARY KEY (address_id);

ALTER TABLE ONLY applicant_education_backgrounds
    ADD CONSTRAINT applicant_education_bg_pkey PRIMARY KEY (applicant_education_background_id);

ALTER TABLE ONLY applicant_health_inclusions
    ADD CONSTRAINT applicant_health_inc_pkey PRIMARY KEY (applicant_health_inclusion_id);

ALTER TABLE ONLY applicant_health_inclusions
    ADD CONSTRAINT applicant_health_inc_unique_app UNIQUE (enrollment_application_id);

ALTER TABLE ONLY applicant_preferences
    ADD CONSTRAINT applicant_pref_pkey PRIMARY KEY (applicant_preference_id);

ALTER TABLE ONLY applicant_preferences
    ADD CONSTRAINT applicant_pref_unique_app UNIQUE (enrollment_application_id);

ALTER TABLE ONLY applicant_responsibles
    ADD CONSTRAINT applicant_resp_pkey PRIMARY KEY (applicant_responsible_id);

ALTER TABLE ONLY applicant_responsibles
    ADD CONSTRAINT applicant_resp_unique_app UNIQUE (enrollment_application_id);

ALTER TABLE ONLY audit_events
    ADD CONSTRAINT audit_events_pkey PRIMARY KEY (audit_event_id);

ALTER TABLE ONLY cities
    ADD CONSTRAINT cities_pkey PRIMARY KEY (city_id);

ALTER TABLE ONLY cities
    ADD CONSTRAINT cities_province_georef_unique UNIQUE (province_id, georef_id);

ALTER TABLE ONLY countries
    ADD CONSTRAINT countries_iso_code_unique UNIQUE (iso_code);

ALTER TABLE ONLY countries
    ADD CONSTRAINT countries_name_unique UNIQUE (name);

ALTER TABLE ONLY countries
    ADD CONSTRAINT countries_pkey PRIMARY KEY (country_id);

ALTER TABLE ONLY course_class_days
    ADD CONSTRAINT course_class_days_class_day_unique UNIQUE (course_class_id, day_of_week);

ALTER TABLE ONLY course_class_days
    ADD CONSTRAINT course_class_days_institution_id_id_unique UNIQUE (institution_id, course_class_day_id);

ALTER TABLE ONLY course_class_days
    ADD CONSTRAINT course_class_days_pkey PRIMARY KEY (course_class_day_id);

ALTER TABLE ONLY course_class_schedules
    ADD CONSTRAINT course_class_schedules_pkey PRIMARY KEY (course_class_schedule_id);

ALTER TABLE ONLY course_class_schedules
    ADD CONSTRAINT course_class_schedules_tenant_id_unique UNIQUE (institution_id, course_class_schedule_id);

ALTER TABLE ONLY course_class_teachers
    ADD CONSTRAINT course_class_teachers_pkey PRIMARY KEY (course_class_teacher_id);

ALTER TABLE ONLY course_class_teachers
    ADD CONSTRAINT course_class_teachers_unique UNIQUE (course_class_id, person_id);

ALTER TABLE ONLY course_classes
    ADD CONSTRAINT course_classes_course_class_number_unique UNIQUE (course_id, class_number);

ALTER TABLE ONLY course_classes
    ADD CONSTRAINT course_classes_course_tenant_unique UNIQUE (institution_id, course_id, course_class_id);

ALTER TABLE ONLY course_classes
    ADD CONSTRAINT course_classes_institution_id_id_unique UNIQUE (institution_id, course_class_id);

ALTER TABLE ONLY course_classes
    ADD CONSTRAINT course_classes_pkey PRIMARY KEY (course_class_id);

ALTER TABLE ONLY course_enrollment_histories
    ADD CONSTRAINT course_enrollment_histories_pkey PRIMARY KEY (course_enrollment_history_id);

ALTER TABLE ONLY course_enrollment_schedules
    ADD CONSTRAINT course_enrollment_schedules_enrollment_schedule_unique UNIQUE (course_enrollment_id, course_class_schedule_id, course_individual_slot_id);

ALTER TABLE ONLY course_enrollment_schedules
    ADD CONSTRAINT course_enrollment_schedules_pkey PRIMARY KEY (course_enrollment_schedule_id);

ALTER TABLE ONLY course_enrollments
    ADD CONSTRAINT course_enrollments_application_course_unique UNIQUE (enrollment_application_course_id);

ALTER TABLE ONLY course_enrollments
    ADD CONSTRAINT course_enrollments_pkey PRIMARY KEY (course_enrollment_id);

ALTER TABLE course_enrollments
    ADD CONSTRAINT course_enrollments_situation_check CHECK (((((status)::text = 'ENROLLED'::text) AND ((academic_status)::text = 'IN_PROGRESS'::text)) OR (((status)::text = 'COMPLETED'::text) AND ((academic_status)::text = ANY ((ARRAY['PENDING_RESULT'::character varying, 'REGULARIZED'::character varying, 'PROMOTED'::character varying, 'PASSED'::character varying, 'FAILED'::character varying])::text[]))) OR (((status)::text = ANY ((ARRAY['WITHDRAWN'::character varying, 'ADMINISTRATIVELY_WITHDRAWN'::character varying])::text[])) AND ((academic_status)::text = 'NOT_APPLICABLE'::text))));

ALTER TABLE ONLY course_enrollments
    ADD CONSTRAINT course_enrollments_tenant_id_unique UNIQUE (institution_id, course_enrollment_id);

ALTER TABLE ONLY course_individual_slots
    ADD CONSTRAINT course_individual_slots_pkey PRIMARY KEY (course_individual_slot_id);

ALTER TABLE ONLY course_individual_slots
    ADD CONSTRAINT course_individual_slots_schedule_id_unique UNIQUE (institution_id, course_class_schedule_id, course_individual_slot_id);

ALTER TABLE ONLY course_individual_slots
    ADD CONSTRAINT course_individual_slots_schedule_time_unique UNIQUE (course_class_schedule_id, start_time, end_time);

ALTER TABLE ONLY course_waitlist_sequences
    ADD CONSTRAINT course_waitlist_sequences_pkey PRIMARY KEY (course_id);

ALTER TABLE ONLY courses
    ADD CONSTRAINT courses_institution_id_id_unique UNIQUE (institution_id, course_id);

ALTER TABLE ONLY courses
    ADD CONSTRAINT courses_pkey PRIMARY KEY (course_id);

ALTER TABLE ONLY document_definitions
    ADD CONSTRAINT document_definitions_institution_id_id_key UNIQUE (institution_id, id);

ALTER TABLE ONLY document_definitions
    ADD CONSTRAINT document_definitions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY enrollment_application_course_assignment_snapshots
    ADD CONSTRAINT eac_snapshot_assignment_unique UNIQUE (enrollment_application_course_id, course_class_schedule_id, course_individual_slot_id);

ALTER TABLE ONLY institutional_email_verification_tokens
    ADD CONSTRAINT email_verification_hash_unique UNIQUE (token_hash);

ALTER TABLE ONLY institutional_email_verification_tokens
    ADD CONSTRAINT email_verification_user_unique UNIQUE (user_id);

ALTER TABLE ONLY enrollment_admission_history
    ADD CONSTRAINT enrollment_admission_history_pkey PRIMARY KEY (id);

ALTER TABLE ONLY enrollment_application_course_assignment_snapshots
    ADD CONSTRAINT enrollment_application_course_assignment_snapshots_pkey PRIMARY KEY (enrollment_application_course_assignment_snapshot_id);

ALTER TABLE ONLY enrollment_application_courses
    ADD CONSTRAINT enrollment_application_courses_app_course_unique UNIQUE (enrollment_application_id, course_id);

ALTER TABLE ONLY enrollment_application_courses
    ADD CONSTRAINT enrollment_application_courses_pkey PRIMARY KEY (enrollment_application_course_id);

ALTER TABLE ONLY enrollment_application_courses
    ADD CONSTRAINT enrollment_application_courses_tenant_id_unique UNIQUE (institution_id, enrollment_application_course_id);

ALTER TABLE ONLY enrollment_application_periods
    ADD CONSTRAINT enrollment_application_periods_pkey PRIMARY KEY (enrollment_application_id, enrollment_period_id);

ALTER TABLE ONLY enrollment_applications
    ADD CONSTRAINT enrollment_applications_pkey PRIMARY KEY (enrollment_application_id);

ALTER TABLE ONLY enrollment_applications
    ADD CONSTRAINT enrollment_applications_tenant_id_unique UNIQUE (institution_id, enrollment_application_id);

ALTER TABLE ONLY enrollment_attachments
    ADD CONSTRAINT enrollment_att_pkey PRIMARY KEY (enrollment_attachment_id);

ALTER TABLE ONLY enrollment_document_audit
    ADD CONSTRAINT enrollment_document_audit_pkey PRIMARY KEY (id);

ALTER TABLE ONLY enrollment_document_requirements
    ADD CONSTRAINT enrollment_document_definition_unique UNIQUE (application_id, document_id);

ALTER TABLE ONLY enrollment_document_requests
    ADD CONSTRAINT enrollment_document_requests_id_application_id_institution__key UNIQUE (id, application_id, institution_id);

ALTER TABLE ONLY enrollment_document_requests
    ADD CONSTRAINT enrollment_document_requests_pkey PRIMARY KEY (id);

ALTER TABLE ONLY enrollment_document_requirements
    ADD CONSTRAINT enrollment_document_requireme_application_id_source_require_key UNIQUE (application_id, source_requirement_id);

ALTER TABLE ONLY enrollment_document_requirements
    ADD CONSTRAINT enrollment_document_requirements_id_application_id_key UNIQUE (id, application_id);

ALTER TABLE ONLY enrollment_document_requirements
    ADD CONSTRAINT enrollment_document_requirements_pkey PRIMARY KEY (id);

ALTER TABLE ONLY enrollment_form_assignments
    ADD CONSTRAINT enrollment_form_assignments_context_unique UNIQUE (institution_id, training_path_id, academic_year_id);

ALTER TABLE ONLY enrollment_form_assignments
    ADD CONSTRAINT enrollment_form_assignments_pkey PRIMARY KEY (id);

ALTER TABLE ONLY enrollment_form_assignments
    ADD CONSTRAINT enrollment_form_assignments_tenant_unique UNIQUE (institution_id, id);

ALTER TABLE ONLY enrollment_form_versions
    ADD CONSTRAINT enrollment_form_versions_number_unique UNIQUE (form_id, version_number);

ALTER TABLE ONLY enrollment_form_versions
    ADD CONSTRAINT enrollment_form_versions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY enrollment_form_versions
    ADD CONSTRAINT enrollment_form_versions_tenant_unique UNIQUE (institution_id, id);

ALTER TABLE ONLY enrollment_forms
    ADD CONSTRAINT enrollment_forms_pkey PRIMARY KEY (id);

ALTER TABLE ONLY enrollment_forms
    ADD CONSTRAINT enrollment_forms_tenant_unique UNIQUE (institution_id, id);

ALTER TABLE ONLY enrollment_pdf_settings
    ADD CONSTRAINT enrollment_pdf_settings_pkey PRIMARY KEY (institution_id);

ALTER TABLE ONLY enrollment_period_offering_levels
    ADD CONSTRAINT enrollment_period_level_unique UNIQUE NULLS NOT DISTINCT (offering_id, academic_level_id);

ALTER TABLE ONLY enrollment_period_offering_levels
    ADD CONSTRAINT enrollment_period_offering_levels_pkey PRIMARY KEY (offering_level_id);

ALTER TABLE ONLY enrollment_period_offerings
    ADD CONSTRAINT enrollment_period_offerings_id_plan_unique UNIQUE (offering_id, study_plan_id);

ALTER TABLE ONLY enrollment_period_offerings
    ADD CONSTRAINT enrollment_period_offerings_pkey PRIMARY KEY (offering_id);

ALTER TABLE ONLY enrollment_period_offerings
    ADD CONSTRAINT enrollment_period_offerings_plan_unique UNIQUE (enrollment_period_id, study_plan_id);

ALTER TABLE ONLY enrollment_period_offering_levels
    ADD CONSTRAINT enrollment_period_scope_overlap EXCLUDE USING gist (institution_id WITH =, academic_year_id WITH =, study_plan_id WITH =, COALESCE(academic_level_id, '00000000-0000-0000-0000-000000000000'::uuid) WITH =, availability WITH &&) WHERE (reserves);

ALTER TABLE ONLY enrollment_periods
    ADD CONSTRAINT enrollment_periods_pkey PRIMARY KEY (enrollment_period_id);

ALTER TABLE ONLY enrollment_periods
    ADD CONSTRAINT enrollment_periods_tenant_id_unique UNIQUE (institution_id, enrollment_period_id);

ALTER TABLE ONLY enrollment_requirement_changes
    ADD CONSTRAINT enrollment_requirement_changes_pkey PRIMARY KEY (id);

ALTER TABLE ONLY enrollment_storage_jobs
    ADD CONSTRAINT enrollment_storage_jobs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY institution_enrollment_locks
    ADD CONSTRAINT institution_enrollment_locks_pkey PRIMARY KEY (institution_id);

ALTER TABLE ONLY institutional_email_verification_tokens
    ADD CONSTRAINT institutional_email_verification_tokens_pkey PRIMARY KEY (email_verification_token_id);

ALTER TABLE ONLY institutional_password_reset_tokens
    ADD CONSTRAINT institutional_password_reset_tokens_pkey PRIMARY KEY (password_reset_token_id);

ALTER TABLE ONLY institutional_password_reset_tokens
    ADD CONSTRAINT institutional_password_reset_tokens_token_hash_key UNIQUE (token_hash);

ALTER TABLE ONLY institutions
    ADD CONSTRAINT institutions_pkey PRIMARY KEY (institution_id);

ALTER TABLE ONLY institutions
    ADD CONSTRAINT institutions_public_subdomain_unique UNIQUE (public_subdomain);

ALTER TABLE ONLY instruments
    ADD CONSTRAINT instruments_institution_id_id_unique UNIQUE (institution_id, instrument_id);

ALTER TABLE ONLY instruments
    ADD CONSTRAINT instruments_pkey PRIMARY KEY (instrument_id);

ALTER TABLE ONLY passkey_credentials
    ADD CONSTRAINT passkey_credentials_credential_id_key UNIQUE (credential_id);

ALTER TABLE ONLY passkey_credentials
    ADD CONSTRAINT passkey_credentials_pkey PRIMARY KEY (passkey_credential_id);

ALTER TABLE ONLY people
    ADD CONSTRAINT people_institution_id_id_unique UNIQUE (institution_id, person_id);

ALTER TABLE ONLY people
    ADD CONSTRAINT people_pkey PRIMARY KEY (person_id);

ALTER TABLE ONLY permissions
    ADD CONSTRAINT permissions_code_unique UNIQUE (code);

ALTER TABLE ONLY permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (permission_id);

ALTER TABLE ONLY person_access_invitations
    ADD CONSTRAINT person_access_invitations_pkey PRIMARY KEY (invitation_id);

ALTER TABLE ONLY person_access_invitations
    ADD CONSTRAINT person_access_invitations_token_hash_key UNIQUE (token_hash);

ALTER TABLE ONLY person_guardian_attachments
    ADD CONSTRAINT person_guardian_attachments_pkey PRIMARY KEY (person_guardian_attachment_id);

ALTER TABLE ONLY person_guardians
    ADD CONSTRAINT person_guardians_institution_id_id_unique UNIQUE (institution_id, person_guardian_id);

ALTER TABLE ONLY person_guardians
    ADD CONSTRAINT person_guardians_invitation_subject_unique UNIQUE (person_guardian_id, dependent_person_id);

ALTER TABLE ONLY person_guardians
    ADD CONSTRAINT person_guardians_pkey PRIMARY KEY (person_guardian_id);

ALTER TABLE ONLY person_role_assignment_training_paths
    ADD CONSTRAINT person_role_assignment_training_paths_pkey PRIMARY KEY (person_role_assignment_id, training_path_id);

ALTER TABLE ONLY person_role_assignments
    ADD CONSTRAINT person_role_assignments_pkey PRIMARY KEY (person_role_assignment_id);

ALTER TABLE ONLY person_role_assignments
    ADD CONSTRAINT person_role_assignments_tenant_id_unique UNIQUE (institution_id, person_role_assignment_id);

ALTER TABLE ONLY person_role_assignments
    ADD CONSTRAINT person_role_assignments_unique UNIQUE (person_id, role_id, institution_id);

ALTER TABLE ONLY platform_account_roles
    ADD CONSTRAINT platform_account_roles_pkey PRIMARY KEY (platform_account_role_id);

ALTER TABLE ONLY platform_account_roles
    ADD CONSTRAINT platform_account_roles_unique UNIQUE (platform_account_id, role_id);

ALTER TABLE ONLY platform_accounts
    ADD CONSTRAINT platform_accounts_email_unique UNIQUE (email);

ALTER TABLE ONLY platform_accounts
    ADD CONSTRAINT platform_accounts_pkey PRIMARY KEY (platform_account_id);

ALTER TABLE ONLY platform_email_change_tokens
    ADD CONSTRAINT platform_email_change_account_unique UNIQUE (platform_account_id);

ALTER TABLE ONLY platform_email_change_tokens
    ADD CONSTRAINT platform_email_change_token_unique UNIQUE (token_hash);

ALTER TABLE ONLY platform_email_change_tokens
    ADD CONSTRAINT platform_email_change_tokens_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform_passkey_credentials
    ADD CONSTRAINT platform_passkey_credentials_credential_id_key UNIQUE (credential_id);

ALTER TABLE ONLY platform_passkey_credentials
    ADD CONSTRAINT platform_passkey_credentials_pkey PRIMARY KEY (platform_passkey_credential_id);

ALTER TABLE ONLY platform_refresh_tokens
    ADD CONSTRAINT platform_refresh_tokens_pkey PRIMARY KEY (platform_refresh_token_id);

ALTER TABLE ONLY platform_refresh_tokens
    ADD CONSTRAINT platform_refresh_tokens_token_hash_key UNIQUE (token_hash);

ALTER TABLE ONLY platform_sessions
    ADD CONSTRAINT platform_sessions_pkey PRIMARY KEY (platform_session_id);

ALTER TABLE ONLY prerequisites
    ADD CONSTRAINT prerequisites_pkey PRIMARY KEY (prerequisite_id);

ALTER TABLE ONLY prerequisites
    ADD CONSTRAINT prerequisites_study_plan_id_id_unique UNIQUE (study_plan_id, prerequisite_id);

ALTER TABLE ONLY prerequisites
    ADD CONSTRAINT prerequisites_target_required_stage_unique UNIQUE (target_study_plan_space_id, required_study_plan_space_id, requirement_stage);

ALTER TABLE ONLY provinces
    ADD CONSTRAINT provinces_country_georef_unique UNIQUE (country_id, georef_id);

ALTER TABLE ONLY provinces
    ADD CONSTRAINT provinces_country_name_unique UNIQUE (country_id, name);

ALTER TABLE ONLY provinces
    ADD CONSTRAINT provinces_pkey PRIMARY KEY (province_id);

ALTER TABLE ONLY refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (refresh_token_id);

ALTER TABLE ONLY refresh_tokens
    ADD CONSTRAINT refresh_tokens_token_hash_key UNIQUE (token_hash);

ALTER TABLE ONLY role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (permission_id, role_id);

ALTER TABLE ONLY roles
    ADD CONSTRAINT roles_institution_id_role_id_unique UNIQUE (institution_id, role_id);

ALTER TABLE ONLY roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (role_id);

ALTER TABLE ONLY roles
    ADD CONSTRAINT roles_scope_code_institution_unique UNIQUE (scope, code, institution_id);

ALTER TABLE ONLY shifts
    ADD CONSTRAINT shifts_institution_id_id_unique UNIQUE (institution_id, shift_id);

ALTER TABLE ONLY shifts
    ADD CONSTRAINT shifts_pkey PRIMARY KEY (shift_id);

ALTER TABLE ONLY students
    ADD CONSTRAINT students_file_number_unique UNIQUE (institution_id, file_number);

ALTER TABLE ONLY students
    ADD CONSTRAINT students_institution_id_id_unique UNIQUE (institution_id, student_id);

ALTER TABLE ONLY students
    ADD CONSTRAINT students_person_id_unique UNIQUE (institution_id, person_id);

ALTER TABLE ONLY students
    ADD CONSTRAINT students_pkey PRIMARY KEY (student_id);

ALTER TABLE ONLY study_plan_space_instruments
    ADD CONSTRAINT study_plan_space_instruments_institution_id_id_unique UNIQUE (institution_id, study_plan_space_instrument_id);

ALTER TABLE ONLY study_plan_space_instruments
    ADD CONSTRAINT study_plan_space_instruments_pkey PRIMARY KEY (study_plan_space_instrument_id);

ALTER TABLE ONLY study_plan_space_instruments
    ADD CONSTRAINT study_plan_space_instruments_space_instrument_unique UNIQUE (study_plan_space_id, instrument_id);

ALTER TABLE ONLY study_plan_spaces
    ADD CONSTRAINT study_plan_spaces_institution_id_id_unique UNIQUE (institution_id, study_plan_space_id);

ALTER TABLE ONLY study_plan_spaces
    ADD CONSTRAINT study_plan_spaces_pkey PRIMARY KEY (study_plan_space_id);

ALTER TABLE ONLY study_plan_spaces
    ADD CONSTRAINT study_plan_spaces_plan_id_id_unique UNIQUE (study_plan_id, study_plan_space_id);

ALTER TABLE ONLY study_plans
    ADD CONSTRAINT study_plans_id_training_path_unique UNIQUE (study_plan_id, training_path_id);

ALTER TABLE ONLY study_plans
    ADD CONSTRAINT study_plans_institution_id_id_unique UNIQUE (institution_id, study_plan_id);

ALTER TABLE ONLY study_plans
    ADD CONSTRAINT study_plans_pkey PRIMARY KEY (study_plan_id);

ALTER TABLE ONLY training_path_document_requirements
    ADD CONSTRAINT training_path_document_requirements_pkey PRIMARY KEY (id);

ALTER TABLE ONLY training_path_document_requirements
    ADD CONSTRAINT training_path_document_unique UNIQUE (training_path_id, document_id);

ALTER TABLE ONLY training_paths
    ADD CONSTRAINT training_paths_institution_id_id_unique UNIQUE (institution_id, training_path_id);

ALTER TABLE ONLY training_paths
    ADD CONSTRAINT training_paths_pkey PRIMARY KEY (training_path_id);

ALTER TABLE ONLY user_sessions
    ADD CONSTRAINT user_sessions_pkey PRIMARY KEY (user_session_id);

ALTER TABLE ONLY users
    ADD CONSTRAINT users_institution_person_unique UNIQUE (institution_id, person_id);

ALTER TABLE ONLY users
    ADD CONSTRAINT users_pkey PRIMARY KEY (user_id);

ALTER TABLE ONLY users
    ADD CONSTRAINT users_webauthn_user_handle_key UNIQUE (webauthn_user_handle);

CREATE INDEX academic_exceptions_person_course_idx ON academic_enrollment_exceptions USING btree (institution_id, person_id, course_id);

CREATE UNIQUE INDEX academic_levels_study_plan_name_unique ON academic_levels USING btree (study_plan_id, lower(translate((name)::text, 'áéíóúüñÁÉÍÓÚÜÑ'::text, 'aeiouunAEIOUUN'::text)));

CREATE INDEX academic_lifecycle_events_resource_idx ON academic_lifecycle_events USING btree (institution_id, resource_type, resource_id, created_at DESC);

CREATE INDEX academic_spaces_contextual_search_idx ON academic_spaces USING gin (bemol_search_vector((name)::text));

CREATE UNIQUE INDEX academic_spaces_current_institution_name_type_format_unique ON academic_spaces USING btree (institution_id, lower(translate((name)::text, 'áéíóúüñÁÉÍÓÚÜÑ'::text, 'aeiouunAEIOUUN'::text)), type, format) WHERE (deleted_at IS NULL);

CREATE INDEX academic_spaces_institution_deleted_at_idx ON academic_spaces USING btree (institution_id, deleted_at);

CREATE INDEX academic_spaces_name_search_trgm_idx ON academic_spaces USING gin (bemol_normalize_text((name)::text) gin_trgm_ops) WHERE (deleted_at IS NULL);

CREATE UNIQUE INDEX academic_years_active_institution_unique ON academic_years USING btree (institution_id) WHERE (((status)::text = 'ACTIVE'::text) AND (deleted_at IS NULL));

CREATE INDEX academic_years_contextual_search_idx ON academic_years USING gin (bemol_search_vector((year)::text));

CREATE UNIQUE INDEX academic_years_current_institution_year_unique ON academic_years USING btree (institution_id, year) WHERE (deleted_at IS NULL);

CREATE INDEX academic_years_institution_deleted_at_idx ON academic_years USING btree (institution_id, deleted_at);

CREATE INDEX audit_events_actor_idx ON audit_events USING btree (institution_id, actor_person_id, occurred_at DESC);

CREATE INDEX audit_events_entity_idx ON audit_events USING btree (institution_id, entity_id, occurred_at DESC);

CREATE INDEX audit_events_global_timeline_idx ON audit_events USING btree (occurred_at DESC, audit_event_id DESC);

CREATE INDEX audit_events_subject_idx ON audit_events USING btree (institution_id, subject_person_id, occurred_at DESC);

CREATE INDEX cities_name_search_trgm_idx ON cities USING gin (bemol_normalize_text((name)::text) gin_trgm_ops);

CREATE INDEX countries_name_search_trgm_idx ON countries USING gin (bemol_normalize_text((name)::text) gin_trgm_ops);

CREATE INDEX course_class_schedules_day_idx ON course_class_schedules USING btree (course_class_day_id);

CREATE INDEX course_classes_course_idx ON course_classes USING btree (course_id);

CREATE INDEX course_enrollment_histories_enrollment_changed_idx ON course_enrollment_histories USING btree (course_enrollment_id, changed_at DESC);

CREATE INDEX course_enrollment_schedules_active_day_idx ON course_enrollment_schedules USING btree (institution_id, day_of_week, start_time, end_time) WHERE (released_at IS NULL);

CREATE UNIQUE INDEX course_enrollment_schedules_active_slot_unique ON course_enrollment_schedules USING btree (institution_id, course_individual_slot_id) WHERE ((released_at IS NULL) AND (course_individual_slot_id IS NOT NULL));

CREATE UNIQUE INDEX course_enrollment_schedules_group_assignment_unique ON course_enrollment_schedules USING btree (course_enrollment_id, course_class_schedule_id) WHERE (course_individual_slot_id IS NULL);

CREATE UNIQUE INDEX course_enrollments_active_student_course_unique ON course_enrollments USING btree (institution_id, student_id, course_id) WHERE ((status)::text = 'ENROLLED'::text);

CREATE INDEX course_enrollments_course_status_idx ON course_enrollments USING btree (institution_id, course_id, status);

CREATE INDEX course_enrollments_institution_enrolled_idx ON course_enrollments USING btree (institution_id, enrolled_at DESC, course_enrollment_id);

CREATE INDEX course_enrollments_institution_student_idx ON course_enrollments USING btree (institution_id, student_id, enrolled_at DESC);

CREATE UNIQUE INDEX courses_curriculum_level_unique ON courses USING btree (institution_id, training_path_id, academic_space_id, academic_level_id, academic_year_id) WHERE ((deleted_at IS NULL) AND (instrument_id IS NULL) AND (academic_level_id IS NOT NULL));

CREATE UNIQUE INDEX courses_curriculum_without_level_unique ON courses USING btree (institution_id, training_path_id, academic_space_id, academic_year_id) WHERE ((deleted_at IS NULL) AND (instrument_id IS NULL) AND (academic_level_id IS NULL));

CREATE INDEX courses_institution_deleted_at_idx ON courses USING btree (institution_id, deleted_at);

CREATE UNIQUE INDEX courses_instrumental_curriculum_level_unique ON courses USING btree (institution_id, training_path_id, academic_space_id, academic_level_id, academic_year_id, instrument_id) WHERE ((deleted_at IS NULL) AND (instrument_id IS NOT NULL) AND (academic_level_id IS NOT NULL));

CREATE UNIQUE INDEX courses_instrumental_curriculum_without_level_unique ON courses USING btree (institution_id, training_path_id, academic_space_id, academic_year_id, instrument_id) WHERE ((deleted_at IS NULL) AND (instrument_id IS NOT NULL) AND (academic_level_id IS NULL));

CREATE INDEX document_definitions_institution_idx ON document_definitions USING btree (institution_id, active, name, id);

CREATE UNIQUE INDEX eac_snapshot_group_assignment_unique ON enrollment_application_course_assignment_snapshots USING btree (enrollment_application_course_id, course_class_schedule_id) WHERE (course_individual_slot_id IS NULL);

CREATE INDEX enrollment_admission_history_application_idx ON enrollment_admission_history USING btree (application_id, occurred_at, id);

CREATE INDEX enrollment_application_courses_application_idx ON enrollment_application_courses USING btree (enrollment_application_id);

CREATE INDEX enrollment_application_courses_course_status_idx ON enrollment_application_courses USING btree (institution_id, course_id, status);

CREATE INDEX enrollment_application_courses_period_idx ON enrollment_application_courses USING btree (enrollment_period_id);

CREATE UNIQUE INDEX enrollment_application_courses_waitlist_number_unique ON enrollment_application_courses USING btree (institution_id, course_id, waitlist_number) WHERE (waitlist_number IS NOT NULL);

CREATE INDEX enrollment_application_periods_period_idx ON enrollment_application_periods USING btree (enrollment_period_id);

CREATE INDEX enrollment_applications_form_version_idx ON enrollment_applications USING btree (form_version_id) WHERE (form_version_id IS NOT NULL);

CREATE INDEX enrollment_applications_institution_status_idx ON enrollment_applications USING btree (institution_id, status) WHERE (deleted_at IS NULL);

CREATE INDEX enrollment_applications_period_idx ON enrollment_applications USING btree (enrollment_period_id);

CREATE INDEX enrollment_applications_submitted_by_idx ON enrollment_applications USING btree (institution_id, submitted_by_person_id);

CREATE UNIQUE INDEX enrollment_apps_applicant_open_path_period_unique ON enrollment_applications USING btree (institution_id, applicant_person_id, training_path_id, enrollment_period_id) WHERE ((deleted_at IS NULL) AND ((status)::text = 'DRAFT'::text));

CREATE UNIQUE INDEX enrollment_apps_applicant_open_path_year_unique ON enrollment_applications USING btree (institution_id, applicant_person_id, training_path_id, academic_year_id) WHERE ((deleted_at IS NULL) AND ((status)::text = 'DRAFT'::text) AND (enrollment_period_id IS NULL) AND (academic_year_id IS NOT NULL));

CREATE INDEX enrollment_att_application_idx ON enrollment_attachments USING btree (enrollment_application_id);

CREATE INDEX enrollment_attachment_history_idx ON enrollment_attachments USING btree (enrollment_application_id, requirement_id, created_at DESC);

CREATE UNIQUE INDEX enrollment_attachments_current_requirement_unique ON enrollment_attachments USING btree (requirement_id) WHERE (((version_status)::text = 'CURRENT'::text) AND (deleted_at IS NULL));

CREATE INDEX enrollment_document_audit_application_idx ON enrollment_document_audit USING btree (application_id, occurred_at DESC);

CREATE INDEX enrollment_document_audit_institution_idx ON enrollment_document_audit USING btree (institution_id, occurred_at DESC);

CREATE INDEX enrollment_document_requests_application_idx ON enrollment_document_requests USING btree (application_id, created_at, id);

CREATE INDEX enrollment_form_assignments_version_idx ON enrollment_form_assignments USING btree (form_version_id);

CREATE UNIQUE INDEX enrollment_form_versions_draft_unique ON enrollment_form_versions USING btree (form_id) WHERE ((status)::text = 'DRAFT'::text);

CREATE INDEX enrollment_period_levels_period_idx ON enrollment_period_offering_levels USING btree (enrollment_period_id);

CREATE INDEX enrollment_requirement_changes_requirement_idx ON enrollment_requirement_changes USING btree (requirement_id, occurred_at, id);

CREATE UNIQUE INDEX enrollment_storage_jobs_attachment_idx ON enrollment_storage_jobs USING btree (attachment_id) WHERE (attachment_id IS NOT NULL);

CREATE INDEX enrollment_storage_jobs_path_idx ON enrollment_storage_jobs USING btree (storage_path);

CREATE INDEX enrollment_storage_jobs_pending_idx ON enrollment_storage_jobs USING btree (next_attempt_at) WHERE ((state)::text = 'PENDING'::text);

CREATE INDEX idx_person_guardians_tutor_list ON person_guardians USING btree (institution_id, tutor_person_id, created_at, person_guardian_id) WHERE ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACTIVE'::character varying, 'REJECTED'::character varying])::text[]));

CREATE INDEX idx_study_plan_space_instruments_institution_id ON study_plan_space_instruments USING btree (institution_id);

CREATE INDEX idx_study_plan_space_instruments_instrument_id ON study_plan_space_instruments USING btree (instrument_id);

CREATE INDEX idx_study_plan_space_instruments_study_plan_space_id ON study_plan_space_instruments USING btree (study_plan_space_id);

CREATE UNIQUE INDEX institutional_password_reset_tokens_user_unique ON institutional_password_reset_tokens USING btree (user_id);

CREATE INDEX institutions_contextual_search_idx ON institutions USING gin (bemol_search_vector((name)::text));

CREATE INDEX institutions_name_search_trgm_idx ON institutions USING gin (bemol_normalize_text((name)::text) gin_trgm_ops);

CREATE INDEX instruments_contextual_search_idx ON instruments USING gin (bemol_search_vector((name)::text));

CREATE UNIQUE INDEX instruments_current_institution_name_unique ON instruments USING btree (institution_id, lower(translate((name)::text, 'áéíóúüñÁÉÍÓÚÜÑ'::text, 'aeiouunAEIOUUN'::text))) WHERE (deleted_at IS NULL);

CREATE INDEX instruments_institution_deleted_at_idx ON instruments USING btree (institution_id, deleted_at);

CREATE INDEX instruments_name_search_trgm_idx ON instruments USING gin (bemol_normalize_text((name)::text) gin_trgm_ops) WHERE (deleted_at IS NULL);

CREATE INDEX passkey_credentials_user_active_idx ON passkey_credentials USING btree (user_id) WHERE (revoked_at IS NULL);

CREATE INDEX passkey_credentials_user_id_idx ON passkey_credentials USING btree (user_id);

CREATE UNIQUE INDEX people_active_document_number_unique ON people USING btree (institution_id, document_number) WHERE (deleted = false);

CREATE INDEX people_contextual_search_idx ON people USING gin (bemol_search_vector((((((((((((first_name)::text || ' '::text) || (last_name)::text) || ' '::text) || (last_name)::text) || ' '::text) || (first_name)::text) || ' '::text) || (document_number)::text) || ' '::text) || (COALESCE(email, ''::character varying))::text))) WHERE (NOT deleted);

CREATE INDEX people_document_search_trgm_idx ON people USING gin (document_number gin_trgm_ops) WHERE (NOT deleted);

CREATE INDEX people_email_search_trgm_idx ON people USING gin (bemol_normalize_text((email)::text) gin_trgm_ops) WHERE (NOT deleted);

CREATE INDEX people_full_name_search_trgm_idx ON people USING gin (bemol_normalize_text((((first_name)::text || ' '::text) || (last_name)::text)) gin_trgm_ops) WHERE (NOT deleted);

CREATE INDEX people_institution_deleted_idx ON people USING btree (institution_id, deleted);

CREATE UNIQUE INDEX person_access_invitation_pending_unique ON person_access_invitations USING btree (person_id) WHERE ((consumed_at IS NULL) AND (cancelled_at IS NULL));

CREATE INDEX person_access_invitation_person_idx ON person_access_invitations USING btree (person_id);

CREATE INDEX person_guardian_attachments_link_idx ON person_guardian_attachments USING btree (person_guardian_id);

CREATE INDEX person_guardians_dependent_idx ON person_guardians USING btree (institution_id, dependent_person_id);

CREATE UNIQUE INDEX person_guardians_open_unique ON person_guardians USING btree (institution_id, tutor_person_id, dependent_person_id) WHERE ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACTIVE'::character varying])::text[]));

CREATE UNIQUE INDEX person_guardians_pending_document_unique ON person_guardians USING btree (institution_id, tutor_person_id, declared_document_number) WHERE ((status)::text = 'PENDING'::text);

CREATE INDEX person_guardians_status_idx ON person_guardians USING btree (institution_id, status);

CREATE INDEX person_role_assignment_training_paths_path_idx ON person_role_assignment_training_paths USING btree (training_path_id);

CREATE INDEX person_role_assignments_institution_role_idx ON person_role_assignments USING btree (institution_id, role_id);

CREATE INDEX person_role_assignments_person_institution_idx ON person_role_assignments USING btree (person_id, institution_id);

CREATE INDEX platform_accounts_contextual_search_idx ON platform_accounts USING gin (bemol_search_vector((((((((((first_name)::text || ' '::text) || (last_name)::text) || ' '::text) || (last_name)::text) || ' '::text) || (first_name)::text) || ' '::text) || (email)::text)));

CREATE INDEX platform_accounts_email_search_trgm_idx ON platform_accounts USING gin (lower((email)::text) gin_trgm_ops);

CREATE INDEX platform_accounts_email_upper_idx ON platform_accounts USING btree (upper((email)::text));

CREATE INDEX platform_accounts_full_name_search_trgm_idx ON platform_accounts USING gin (bemol_normalize_text((((first_name)::text || ' '::text) || (last_name)::text)) gin_trgm_ops);

CREATE INDEX platform_passkey_credentials_account_active_idx ON platform_passkey_credentials USING btree (platform_account_id) WHERE (revoked_at IS NULL);

CREATE INDEX platform_passkey_credentials_account_id_idx ON platform_passkey_credentials USING btree (platform_account_id);

CREATE INDEX platform_refresh_tokens_family_idx ON platform_refresh_tokens USING btree (family_id);

CREATE INDEX platform_refresh_tokens_session_idx ON platform_refresh_tokens USING btree (platform_session_id);

CREATE INDEX platform_sessions_account_active_idx ON platform_sessions USING btree (platform_account_id, active);

CREATE INDEX prerequisites_plan_target_idx ON prerequisites USING btree (study_plan_id, target_study_plan_space_id);

CREATE INDEX provinces_name_search_trgm_idx ON provinces USING gin (bemol_normalize_text((name)::text) gin_trgm_ops);

CREATE INDEX refresh_tokens_family_idx ON refresh_tokens USING btree (family_id);

CREATE INDEX refresh_tokens_session_idx ON refresh_tokens USING btree (session_id);

CREATE INDEX role_permissions_role_idx ON role_permissions USING btree (role_id);

CREATE INDEX roles_code_search_trgm_idx ON roles USING gin (bemol_normalize_text((code)::text) gin_trgm_ops) WHERE ((scope)::text = 'INSTITUTION'::text);

CREATE INDEX roles_contextual_search_idx ON roles USING gin (bemol_search_vector((((name)::text || ' '::text) || (code)::text))) WHERE ((scope)::text = 'INSTITUTION'::text);

CREATE UNIQUE INDEX roles_institution_name_lower_unique ON roles USING btree (institution_id, lower((name)::text)) WHERE (institution_id IS NOT NULL);

CREATE INDEX roles_name_search_trgm_idx ON roles USING gin (bemol_normalize_text((name)::text) gin_trgm_ops) WHERE ((scope)::text = 'INSTITUTION'::text);

CREATE INDEX shifts_contextual_search_idx ON shifts USING gin (bemol_search_vector((name)::text));

CREATE UNIQUE INDEX shifts_current_institution_name_unique ON shifts USING btree (institution_id, lower(translate((name)::text, 'áéíóúüñÁÉÍÓÚÜÑ'::text, 'aeiouunAEIOUUN'::text))) WHERE (deleted_at IS NULL);

CREATE INDEX shifts_institution_deleted_at_idx ON shifts USING btree (institution_id, deleted_at);

CREATE INDEX shifts_name_search_trgm_idx ON shifts USING gin (bemol_normalize_text((name)::text) gin_trgm_ops) WHERE (deleted_at IS NULL);

CREATE INDEX study_plan_spaces_academic_space_plan_idx ON study_plan_spaces USING btree (academic_space_id, study_plan_id);

CREATE UNIQUE INDEX study_plan_spaces_plan_level_order_unique ON study_plan_spaces USING btree (study_plan_id, academic_level_id, display_order) WHERE (academic_level_id IS NOT NULL);

CREATE UNIQUE INDEX study_plan_spaces_plan_level_space_unique ON study_plan_spaces USING btree (study_plan_id, academic_level_id, academic_space_id) WHERE (academic_level_id IS NOT NULL);

CREATE INDEX study_plan_spaces_plan_order_idx ON study_plan_spaces USING btree (study_plan_id, display_order);

CREATE UNIQUE INDEX study_plan_spaces_plan_unassigned_order_unique ON study_plan_spaces USING btree (study_plan_id, display_order) WHERE (academic_level_id IS NULL);

CREATE UNIQUE INDEX study_plan_spaces_plan_unassigned_space_unique ON study_plan_spaces USING btree (study_plan_id, academic_space_id) WHERE (academic_level_id IS NULL);

CREATE INDEX study_plans_contextual_search_idx ON study_plans USING gin (bemol_search_vector((name)::text));

CREATE UNIQUE INDEX study_plans_current_successor_unique ON study_plans USING btree (previous_version_id) WHERE ((previous_version_id IS NOT NULL) AND (deleted_at IS NULL));

CREATE UNIQUE INDEX study_plans_current_training_path_name_version_unique ON study_plans USING btree (training_path_id, lower(translate((name)::text, 'áéíóúüñÁÉÍÓÚÜÑ'::text, 'aeiouunAEIOUUN'::text)), version_number) WHERE (deleted_at IS NULL);

CREATE INDEX study_plans_institution_deleted_at_idx ON study_plans USING btree (institution_id, deleted_at);

CREATE INDEX study_plans_name_search_trgm_idx ON study_plans USING gin (bemol_normalize_text((name)::text) gin_trgm_ops) WHERE (deleted_at IS NULL);

CREATE INDEX study_plans_previous_version_idx ON study_plans USING btree (previous_version_id);

CREATE INDEX training_path_document_requirements_path_idx ON training_path_document_requirements USING btree (training_path_id, display_order, id);

CREATE INDEX training_paths_contextual_search_idx ON training_paths USING gin (bemol_search_vector((name)::text));

CREATE UNIQUE INDEX training_paths_current_institution_name_unique ON training_paths USING btree (institution_id, lower(translate((name)::text, 'áéíóúüñÁÉÍÓÚÜÑ'::text, 'aeiouunAEIOUUN'::text))) WHERE (deleted_at IS NULL);

CREATE INDEX training_paths_institution_deleted_at_idx ON training_paths USING btree (institution_id, deleted_at);

CREATE INDEX training_paths_name_search_trgm_idx ON training_paths USING gin (bemol_normalize_text((name)::text) gin_trgm_ops) WHERE (deleted_at IS NULL);

CREATE INDEX user_sessions_user_active_idx ON user_sessions USING btree (user_id, active);

CREATE TRIGGER academic_enrollment_exception_validate BEFORE INSERT OR DELETE OR UPDATE ON academic_enrollment_exceptions FOR EACH ROW EXECUTE FUNCTION validate_academic_enrollment_exception();

CREATE TRIGGER academic_spaces_instantiated_shape BEFORE UPDATE OF instrumental, format ON academic_spaces FOR EACH ROW EXECUTE FUNCTION protect_instantiated_academic_space();

CREATE TRIGGER audit_events_append_only BEFORE DELETE OR UPDATE ON audit_events FOR EACH ROW EXECUTE FUNCTION audit_events_reject_change();

CREATE TRIGGER course_enrollment_academic_requirements BEFORE INSERT OR UPDATE OF academic_status, status ON course_enrollments FOR EACH ROW EXECUTE FUNCTION validate_course_enrollment_academic_requirements();

CREATE TRIGGER course_enrollment_schedules_integrity BEFORE INSERT OR UPDATE ON course_enrollment_schedules FOR EACH ROW EXECUTE FUNCTION validate_course_enrollment_assignment();

CREATE TRIGGER course_enrollments_origin BEFORE INSERT OR UPDATE ON course_enrollments FOR EACH ROW EXECUTE FUNCTION validate_course_enrollment_parent();

CREATE TRIGGER course_individual_slots_schedule_bounds BEFORE INSERT OR UPDATE ON course_individual_slots FOR EACH ROW EXECUTE FUNCTION validate_individual_slot_boundaries();

CREATE TRIGGER courses_curriculum_integrity BEFORE INSERT OR UPDATE OF study_plan_space_id, academic_space_id, academic_level_id, training_path_id, instrument_id, deleted_at ON courses FOR EACH ROW EXECUTE FUNCTION validate_course_instrument_integrity();

CREATE TRIGGER document_definition_protect_institution BEFORE UPDATE OF institution_id, used_at ON document_definitions FOR EACH ROW EXECUTE FUNCTION protect_document_definition_institution();

CREATE TRIGGER enforce_institutional_authority_scope BEFORE INSERT OR UPDATE ON person_role_assignments FOR EACH ROW EXECUTE FUNCTION enforce_institutional_authority_scope();

CREATE TRIGGER enrollment_application_academic_requirements BEFORE INSERT OR UPDATE OF status ON enrollment_applications FOR EACH ROW EXECUTE FUNCTION validate_application_academic_requirements();

CREATE TRIGGER enrollment_application_academic_year BEFORE INSERT OR UPDATE OF academic_year_id, institution_id, enrollment_period_id ON enrollment_applications FOR EACH ROW EXECUTE FUNCTION validate_enrollment_application_academic_year();

CREATE TRIGGER enrollment_application_courses_integrity BEFORE INSERT OR UPDATE ON enrollment_application_courses FOR EACH ROW EXECUTE FUNCTION validate_course_selection_integrity();

CREATE TRIGGER enrollment_application_form BEFORE INSERT OR UPDATE ON enrollment_applications FOR EACH ROW EXECUTE FUNCTION protect_enrollment_application_form();

CREATE TRIGGER enrollment_attachment_immutable BEFORE INSERT OR UPDATE ON enrollment_attachments FOR EACH ROW EXECUTE FUNCTION protect_enrollment_document_delivery();

CREATE CONSTRAINT TRIGGER enrollment_attachment_parent_gate AFTER INSERT OR UPDATE ON enrollment_attachments DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_enrollment_document_delivery_gate();

CREATE TRIGGER enrollment_attachment_requirement_active BEFORE INSERT OR UPDATE ON enrollment_attachments FOR EACH ROW EXECUTE FUNCTION validate_active_enrollment_delivery();

CREATE TRIGGER enrollment_document_gate BEFORE INSERT OR UPDATE OF status ON enrollment_applications FOR EACH ROW EXECUTE FUNCTION validate_enrollment_document_gate();

CREATE TRIGGER enrollment_document_remember_usage AFTER INSERT ON enrollment_document_requirements FOR EACH ROW EXECUTE FUNCTION remember_document_definition_usage();

CREATE TRIGGER enrollment_document_request_immutable BEFORE INSERT OR DELETE OR UPDATE ON enrollment_document_requests FOR EACH ROW EXECUTE FUNCTION protect_enrollment_document_request();

CREATE TRIGGER enrollment_draft_course_period_scope BEFORE INSERT OR UPDATE OF course_id, enrollment_application_id, enrollment_period_id ON enrollment_application_courses FOR EACH ROW EXECUTE FUNCTION validate_draft_course_period_scope();

CREATE TRIGGER enrollment_form_assignment_context BEFORE INSERT OR UPDATE ON enrollment_form_assignments FOR EACH ROW EXECUTE FUNCTION protect_enrollment_form_assignment();

CREATE TRIGGER enrollment_form_identity BEFORE UPDATE ON enrollment_forms FOR EACH ROW EXECUTE FUNCTION protect_enrollment_form_identity();

CREATE TRIGGER enrollment_form_version_lifecycle BEFORE INSERT OR DELETE OR UPDATE ON enrollment_form_versions FOR EACH ROW EXECUTE FUNCTION protect_enrollment_form_version();

CREATE TRIGGER enrollment_period_context_preserve BEFORE UPDATE ON enrollment_periods FOR EACH ROW EXECUTE FUNCTION preserve_enrollment_period_context();

CREATE TRIGGER enrollment_period_level_derive BEFORE INSERT OR UPDATE ON enrollment_period_offering_levels FOR EACH ROW EXECUTE FUNCTION derive_enrollment_period_level();

CREATE TRIGGER enrollment_period_level_preserve BEFORE DELETE ON enrollment_period_offering_levels FOR EACH ROW EXECUTE FUNCTION prevent_enrollment_period_scope_shrink();

CREATE TRIGGER enrollment_period_offering_preserve BEFORE DELETE ON enrollment_period_offerings FOR EACH ROW EXECUTE FUNCTION prevent_enrollment_period_scope_shrink();

CREATE TRIGGER enrollment_period_offering_validate BEFORE INSERT OR UPDATE ON enrollment_period_offerings FOR EACH ROW EXECUTE FUNCTION validate_enrollment_period_offering();

CREATE TRIGGER enrollment_period_reservations_sync AFTER UPDATE OF start_date, end_date, status, deleted_at ON enrollment_periods FOR EACH ROW EXECUTE FUNCTION sync_enrollment_period_reservations();

CREATE TRIGGER enrollment_period_scope_clear_drafts AFTER DELETE ON enrollment_period_offering_levels FOR EACH ROW EXECUTE FUNCTION clear_draft_courses_after_period_scope_reduction();

CREATE CONSTRAINT TRIGGER enrollment_period_scope_required AFTER INSERT OR UPDATE ON enrollment_periods DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_configured_period_offerings();

CREATE CONSTRAINT TRIGGER enrollment_period_scope_retained AFTER DELETE ON enrollment_period_offering_levels DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_configured_period_offerings();

CREATE TRIGGER enrollment_requirement_snapshot BEFORE INSERT OR UPDATE ON enrollment_document_requirements FOR EACH ROW EXECUTE FUNCTION protect_enrollment_requirement_snapshot();

CREATE CONSTRAINT TRIGGER person_role_assignment_paths_check AFTER INSERT OR DELETE OR UPDATE ON person_role_assignment_training_paths DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_person_role_assignment_scope();

CREATE CONSTRAINT TRIGGER person_role_assignment_scope_check AFTER INSERT OR UPDATE ON person_role_assignments DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_person_role_assignment_scope();

CREATE TRIGGER prepare_role_scope_training_path BEFORE INSERT OR UPDATE ON person_role_assignment_training_paths FOR EACH ROW EXECUTE FUNCTION prepare_role_scope_training_path();

CREATE TRIGGER training_path_document_assignment_identity BEFORE UPDATE ON training_path_document_requirements FOR EACH ROW EXECUTE FUNCTION protect_training_path_document_assignment();

CREATE TRIGGER training_path_document_remember_usage AFTER INSERT ON training_path_document_requirements FOR EACH ROW EXECUTE FUNCTION remember_document_definition_usage();

ALTER TABLE ONLY academic_enrollment_exceptions
    ADD CONSTRAINT academic_enrollment_exception_authority_platform_account_i_fkey FOREIGN KEY (authority_platform_account_id) REFERENCES platform_accounts(platform_account_id);

ALTER TABLE ONLY academic_enrollment_exceptions
    ADD CONSTRAINT academic_enrollment_exception_required_study_plan_space_id_fkey FOREIGN KEY (required_study_plan_space_id) REFERENCES study_plan_spaces(study_plan_space_id);

ALTER TABLE ONLY academic_enrollment_exceptions
    ADD CONSTRAINT academic_enrollment_exceptions_prerequisite_id_fkey FOREIGN KEY (prerequisite_id) REFERENCES prerequisites(prerequisite_id);

ALTER TABLE ONLY academic_enrollment_exceptions
    ADD CONSTRAINT academic_exception_authority_fk FOREIGN KEY (institution_id, authority_person_id) REFERENCES people(institution_id, person_id);

ALTER TABLE ONLY academic_enrollment_exceptions
    ADD CONSTRAINT academic_exception_course_fk FOREIGN KEY (institution_id, course_id) REFERENCES courses(institution_id, course_id);

ALTER TABLE ONLY academic_enrollment_exceptions
    ADD CONSTRAINT academic_exception_person_fk FOREIGN KEY (institution_id, person_id) REFERENCES people(institution_id, person_id);

ALTER TABLE ONLY academic_levels
    ADD CONSTRAINT academic_levels_study_plan_fk FOREIGN KEY (study_plan_id) REFERENCES study_plans(study_plan_id);

ALTER TABLE ONLY academic_lifecycle_events
    ADD CONSTRAINT academic_lifecycle_events_institution_fk FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY academic_spaces
    ADD CONSTRAINT academic_spaces_institution_fk FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY academic_years
    ADD CONSTRAINT academic_years_institution_fk FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY account_email_change_tokens
    ADD CONSTRAINT account_email_change_tokens_actor_person_id_fkey FOREIGN KEY (actor_person_id) REFERENCES people(person_id);

ALTER TABLE ONLY account_email_change_tokens
    ADD CONSTRAINT account_email_change_tokens_actor_platform_account_id_fkey FOREIGN KEY (actor_platform_account_id) REFERENCES platform_accounts(platform_account_id);

ALTER TABLE ONLY account_email_change_tokens
    ADD CONSTRAINT account_email_change_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(user_id);

ALTER TABLE ONLY applicant_education_backgrounds
    ADD CONSTRAINT applicant_education_bg_app_fk FOREIGN KEY (enrollment_application_id) REFERENCES enrollment_applications(enrollment_application_id);

ALTER TABLE ONLY applicant_health_inclusions
    ADD CONSTRAINT applicant_health_inc_app_fk FOREIGN KEY (enrollment_application_id) REFERENCES enrollment_applications(enrollment_application_id);

ALTER TABLE ONLY applicant_preferences
    ADD CONSTRAINT applicant_pref_app_fk FOREIGN KEY (enrollment_application_id) REFERENCES enrollment_applications(enrollment_application_id);

ALTER TABLE ONLY applicant_responsibles
    ADD CONSTRAINT applicant_resp_app_fk FOREIGN KEY (enrollment_application_id) REFERENCES enrollment_applications(enrollment_application_id);

ALTER TABLE ONLY audit_events
    ADD CONSTRAINT audit_events_institution_fk FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY course_class_days
    ADD CONSTRAINT course_class_days_class_fk FOREIGN KEY (institution_id, course_class_id) REFERENCES course_classes(institution_id, course_class_id);

ALTER TABLE ONLY course_class_days
    ADD CONSTRAINT course_class_days_institution_fk FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY course_class_schedules
    ADD CONSTRAINT course_class_schedules_day_fk FOREIGN KEY (institution_id, course_class_day_id) REFERENCES course_class_days(institution_id, course_class_day_id);

ALTER TABLE ONLY course_class_schedules
    ADD CONSTRAINT course_class_schedules_institution_fk FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY course_class_teachers
    ADD CONSTRAINT course_class_teachers_class_fk FOREIGN KEY (institution_id, course_class_id) REFERENCES course_classes(institution_id, course_class_id);

ALTER TABLE ONLY course_class_teachers
    ADD CONSTRAINT course_class_teachers_institution_fk FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY course_class_teachers
    ADD CONSTRAINT course_class_teachers_person_institution_fk FOREIGN KEY (institution_id, person_id) REFERENCES people(institution_id, person_id);

ALTER TABLE ONLY course_classes
    ADD CONSTRAINT course_classes_course_fk FOREIGN KEY (institution_id, course_id) REFERENCES courses(institution_id, course_id);

ALTER TABLE ONLY course_classes
    ADD CONSTRAINT course_classes_institution_fk FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY course_enrollment_histories
    ADD CONSTRAINT course_enrollment_histories_authority_fk FOREIGN KEY (authority_person_id) REFERENCES people(person_id);

ALTER TABLE ONLY course_enrollment_histories
    ADD CONSTRAINT course_enrollment_histories_enrollment_fk FOREIGN KEY (course_enrollment_id) REFERENCES course_enrollments(course_enrollment_id);

ALTER TABLE ONLY course_enrollment_histories
    ADD CONSTRAINT course_enrollment_histories_tenant_enrollment_fk FOREIGN KEY (institution_id, course_enrollment_id) REFERENCES course_enrollments(institution_id, course_enrollment_id);

ALTER TABLE ONLY course_enrollment_schedules
    ADD CONSTRAINT course_enrollment_schedules_enrollment_fk FOREIGN KEY (course_enrollment_id) REFERENCES course_enrollments(course_enrollment_id);

ALTER TABLE ONLY course_enrollment_schedules
    ADD CONSTRAINT course_enrollment_schedules_matching_slot_fk FOREIGN KEY (institution_id, course_class_schedule_id, course_individual_slot_id) REFERENCES course_individual_slots(institution_id, course_class_schedule_id, course_individual_slot_id);

ALTER TABLE ONLY course_enrollment_schedules
    ADD CONSTRAINT course_enrollment_schedules_schedule_fk FOREIGN KEY (course_class_schedule_id) REFERENCES course_class_schedules(course_class_schedule_id);

ALTER TABLE ONLY course_enrollment_schedules
    ADD CONSTRAINT course_enrollment_schedules_slot_fk FOREIGN KEY (course_individual_slot_id) REFERENCES course_individual_slots(course_individual_slot_id);

ALTER TABLE ONLY course_enrollment_schedules
    ADD CONSTRAINT course_enrollment_schedules_tenant_enrollment_fk FOREIGN KEY (institution_id, course_enrollment_id) REFERENCES course_enrollments(institution_id, course_enrollment_id);

ALTER TABLE ONLY course_enrollment_schedules
    ADD CONSTRAINT course_enrollment_schedules_tenant_schedule_fk FOREIGN KEY (institution_id, course_class_schedule_id) REFERENCES course_class_schedules(institution_id, course_class_schedule_id);

ALTER TABLE ONLY course_enrollments
    ADD CONSTRAINT course_enrollments_application_course_fk FOREIGN KEY (institution_id, enrollment_application_course_id) REFERENCES enrollment_application_courses(institution_id, enrollment_application_course_id);

ALTER TABLE ONLY course_enrollments
    ADD CONSTRAINT course_enrollments_application_fk FOREIGN KEY (enrollment_application_id) REFERENCES enrollment_applications(enrollment_application_id);

ALTER TABLE ONLY course_enrollments
    ADD CONSTRAINT course_enrollments_authority_fk FOREIGN KEY (action_authority_person_id) REFERENCES people(person_id);

ALTER TABLE ONLY course_enrollments
    ADD CONSTRAINT course_enrollments_class_course_fk FOREIGN KEY (institution_id, course_id, course_class_id) REFERENCES course_classes(institution_id, course_id, course_class_id);

ALTER TABLE ONLY course_enrollments
    ADD CONSTRAINT course_enrollments_class_fk FOREIGN KEY (institution_id, course_class_id) REFERENCES course_classes(institution_id, course_class_id);

ALTER TABLE ONLY course_enrollments
    ADD CONSTRAINT course_enrollments_course_fk FOREIGN KEY (institution_id, course_id) REFERENCES courses(institution_id, course_id);

ALTER TABLE ONLY course_enrollments
    ADD CONSTRAINT course_enrollments_student_fk FOREIGN KEY (institution_id, student_id) REFERENCES students(institution_id, student_id);

ALTER TABLE ONLY course_enrollments
    ADD CONSTRAINT course_enrollments_tenant_application_fk FOREIGN KEY (institution_id, enrollment_application_id) REFERENCES enrollment_applications(institution_id, enrollment_application_id);

ALTER TABLE ONLY course_individual_slots
    ADD CONSTRAINT course_individual_slots_schedule_fk FOREIGN KEY (course_class_schedule_id) REFERENCES course_class_schedules(course_class_schedule_id);

ALTER TABLE ONLY course_individual_slots
    ADD CONSTRAINT course_individual_slots_tenant_schedule_fk FOREIGN KEY (institution_id, course_class_schedule_id) REFERENCES course_class_schedules(institution_id, course_class_schedule_id);

ALTER TABLE ONLY course_waitlist_sequences
    ADD CONSTRAINT course_waitlist_sequences_course_fk FOREIGN KEY (institution_id, course_id) REFERENCES courses(institution_id, course_id);

ALTER TABLE ONLY courses
    ADD CONSTRAINT courses_academic_space_fk FOREIGN KEY (institution_id, academic_space_id) REFERENCES academic_spaces(institution_id, academic_space_id);

ALTER TABLE ONLY courses
    ADD CONSTRAINT courses_academic_year_fk FOREIGN KEY (institution_id, academic_year_id) REFERENCES academic_years(institution_id, academic_year_id);

ALTER TABLE ONLY courses
    ADD CONSTRAINT courses_institution_fk FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY courses
    ADD CONSTRAINT courses_instrument_fk FOREIGN KEY (institution_id, instrument_id) REFERENCES instruments(institution_id, instrument_id);

ALTER TABLE ONLY courses
    ADD CONSTRAINT courses_study_plan_space_fk FOREIGN KEY (institution_id, study_plan_space_id) REFERENCES study_plan_spaces(institution_id, study_plan_space_id);

ALTER TABLE ONLY courses
    ADD CONSTRAINT courses_training_path_fk FOREIGN KEY (institution_id, training_path_id) REFERENCES training_paths(institution_id, training_path_id);

ALTER TABLE ONLY document_definitions
    ADD CONSTRAINT document_definitions_institution_id_fkey FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY enrollment_application_course_assignment_snapshots
    ADD CONSTRAINT eac_snapshot_application_course_fk FOREIGN KEY (enrollment_application_course_id) REFERENCES enrollment_application_courses(enrollment_application_course_id);

ALTER TABLE ONLY enrollment_application_course_assignment_snapshots
    ADD CONSTRAINT eac_snapshot_class_course_fk FOREIGN KEY (institution_id, course_id, course_class_id) REFERENCES course_classes(institution_id, course_id, course_class_id);

ALTER TABLE ONLY enrollment_application_course_assignment_snapshots
    ADD CONSTRAINT eac_snapshot_matching_slot_fk FOREIGN KEY (institution_id, course_class_schedule_id, course_individual_slot_id) REFERENCES course_individual_slots(institution_id, course_class_schedule_id, course_individual_slot_id);

ALTER TABLE ONLY enrollment_application_course_assignment_snapshots
    ADD CONSTRAINT eac_snapshot_tenant_child_fk FOREIGN KEY (institution_id, enrollment_application_course_id) REFERENCES enrollment_application_courses(institution_id, enrollment_application_course_id);

ALTER TABLE ONLY enrollment_application_course_assignment_snapshots
    ADD CONSTRAINT eac_snapshot_tenant_schedule_fk FOREIGN KEY (institution_id, course_class_schedule_id) REFERENCES course_class_schedules(institution_id, course_class_schedule_id);

ALTER TABLE ONLY enrollment_admission_history
    ADD CONSTRAINT enrollment_admission_history_application_id_fkey FOREIGN KEY (application_id) REFERENCES enrollment_applications(enrollment_application_id);

ALTER TABLE ONLY enrollment_application_course_assignment_snapshots
    ADD CONSTRAINT enrollment_application_course_assignment_snapshots_authority_fk FOREIGN KEY (assigned_by_person_id) REFERENCES people(person_id);

ALTER TABLE ONLY enrollment_application_course_assignment_snapshots
    ADD CONSTRAINT enrollment_application_course_assignment_snapshots_course_fk FOREIGN KEY (institution_id, course_id) REFERENCES courses(institution_id, course_id);

ALTER TABLE ONLY enrollment_application_courses
    ADD CONSTRAINT enrollment_application_course_period_tenant_fk FOREIGN KEY (institution_id, enrollment_period_id) REFERENCES enrollment_periods(institution_id, enrollment_period_id);

ALTER TABLE ONLY enrollment_application_courses
    ADD CONSTRAINT enrollment_application_courses_app_fk FOREIGN KEY (enrollment_application_id) REFERENCES enrollment_applications(enrollment_application_id);

ALTER TABLE ONLY enrollment_application_courses
    ADD CONSTRAINT enrollment_application_courses_course_fk FOREIGN KEY (institution_id, course_id) REFERENCES courses(institution_id, course_id);

ALTER TABLE ONLY enrollment_application_courses
    ADD CONSTRAINT enrollment_application_courses_resolver_fk FOREIGN KEY (resolved_by_person_id) REFERENCES people(person_id);

ALTER TABLE ONLY enrollment_application_courses
    ADD CONSTRAINT enrollment_application_courses_teacher_fk FOREIGN KEY (preferred_teacher_id) REFERENCES people(person_id);

ALTER TABLE ONLY enrollment_application_courses
    ADD CONSTRAINT enrollment_application_courses_tenant_parent_fk FOREIGN KEY (institution_id, enrollment_application_id) REFERENCES enrollment_applications(institution_id, enrollment_application_id);

ALTER TABLE ONLY enrollment_application_courses
    ADD CONSTRAINT enrollment_application_courses_tenant_teacher_fk FOREIGN KEY (institution_id, preferred_teacher_id) REFERENCES people(institution_id, person_id);

ALTER TABLE ONLY enrollment_applications
    ADD CONSTRAINT enrollment_application_form_version_tenant_fk FOREIGN KEY (institution_id, form_version_id) REFERENCES enrollment_form_versions(institution_id, id);

ALTER TABLE ONLY enrollment_application_periods
    ADD CONSTRAINT enrollment_application_periods_enrollment_application_id_fkey FOREIGN KEY (enrollment_application_id) REFERENCES enrollment_applications(enrollment_application_id);

ALTER TABLE ONLY enrollment_application_periods
    ADD CONSTRAINT enrollment_application_periods_enrollment_period_id_fkey FOREIGN KEY (enrollment_period_id) REFERENCES enrollment_periods(enrollment_period_id);

ALTER TABLE ONLY enrollment_applications
    ADD CONSTRAINT enrollment_applications_academic_year_fk FOREIGN KEY (academic_year_id) REFERENCES academic_years(academic_year_id);

ALTER TABLE ONLY enrollment_applications
    ADD CONSTRAINT enrollment_applications_institution_fk FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY enrollment_applications
    ADD CONSTRAINT enrollment_applications_period_fk FOREIGN KEY (enrollment_period_id) REFERENCES enrollment_periods(enrollment_period_id);

ALTER TABLE ONLY enrollment_applications
    ADD CONSTRAINT enrollment_applications_person_fk FOREIGN KEY (applicant_person_id) REFERENCES people(person_id);

ALTER TABLE ONLY enrollment_applications
    ADD CONSTRAINT enrollment_applications_resolved_by_fk FOREIGN KEY (resolved_by_person_id) REFERENCES people(person_id);

ALTER TABLE ONLY enrollment_applications
    ADD CONSTRAINT enrollment_applications_submitted_by_institution_fk FOREIGN KEY (institution_id, submitted_by_person_id) REFERENCES people(institution_id, person_id);

ALTER TABLE ONLY enrollment_attachments
    ADD CONSTRAINT enrollment_att_app_fk FOREIGN KEY (enrollment_application_id) REFERENCES enrollment_applications(enrollment_application_id);

ALTER TABLE ONLY enrollment_attachments
    ADD CONSTRAINT enrollment_attachment_requirement_fk FOREIGN KEY (requirement_id, enrollment_application_id) REFERENCES enrollment_document_requirements(id, application_id);

ALTER TABLE ONLY enrollment_document_requirements
    ADD CONSTRAINT enrollment_document_application_tenant_fk FOREIGN KEY (institution_id, application_id) REFERENCES enrollment_applications(institution_id, enrollment_application_id);

ALTER TABLE ONLY enrollment_document_requests
    ADD CONSTRAINT enrollment_document_requests_institution_id_application_id_fkey FOREIGN KEY (institution_id, application_id) REFERENCES enrollment_applications(institution_id, enrollment_application_id);

ALTER TABLE ONLY enrollment_document_requirements
    ADD CONSTRAINT enrollment_document_requirements_application_id_fkey FOREIGN KEY (application_id) REFERENCES enrollment_applications(enrollment_application_id);

ALTER TABLE ONLY enrollment_document_requirements
    ADD CONSTRAINT enrollment_document_tenant_fk FOREIGN KEY (institution_id, document_id) REFERENCES document_definitions(institution_id, id);

ALTER TABLE ONLY enrollment_form_assignments
    ADD CONSTRAINT enrollment_form_assignments_institution_id_fkey FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY enrollment_form_assignments
    ADD CONSTRAINT enrollment_form_assignments_path_tenant_fk FOREIGN KEY (institution_id, training_path_id) REFERENCES training_paths(institution_id, training_path_id);

ALTER TABLE ONLY enrollment_form_assignments
    ADD CONSTRAINT enrollment_form_assignments_version_tenant_fk FOREIGN KEY (institution_id, form_version_id) REFERENCES enrollment_form_versions(institution_id, id);

ALTER TABLE ONLY enrollment_form_assignments
    ADD CONSTRAINT enrollment_form_assignments_year_tenant_fk FOREIGN KEY (institution_id, academic_year_id) REFERENCES academic_years(institution_id, academic_year_id);

ALTER TABLE ONLY enrollment_form_versions
    ADD CONSTRAINT enrollment_form_versions_form_tenant_fk FOREIGN KEY (institution_id, form_id) REFERENCES enrollment_forms(institution_id, id);

ALTER TABLE ONLY enrollment_form_versions
    ADD CONSTRAINT enrollment_form_versions_institution_id_fkey FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY enrollment_forms
    ADD CONSTRAINT enrollment_forms_institution_id_fkey FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY enrollment_pdf_settings
    ADD CONSTRAINT enrollment_pdf_settings_institution_id_fkey FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY enrollment_period_offering_levels
    ADD CONSTRAINT enrollment_period_level_offering_fk FOREIGN KEY (offering_id, study_plan_id) REFERENCES enrollment_period_offerings(offering_id, study_plan_id) ON DELETE CASCADE;

ALTER TABLE ONLY enrollment_period_offering_levels
    ADD CONSTRAINT enrollment_period_level_plan_fk FOREIGN KEY (study_plan_id, academic_level_id) REFERENCES academic_levels(study_plan_id, academic_level_id);

ALTER TABLE ONLY enrollment_period_offering_levels
    ADD CONSTRAINT enrollment_period_level_tenant_fk FOREIGN KEY (institution_id, enrollment_period_id) REFERENCES enrollment_periods(institution_id, enrollment_period_id);

ALTER TABLE ONLY enrollment_period_offerings
    ADD CONSTRAINT enrollment_period_offerings_enrollment_period_id_fkey FOREIGN KEY (enrollment_period_id) REFERENCES enrollment_periods(enrollment_period_id);

ALTER TABLE ONLY enrollment_period_offerings
    ADD CONSTRAINT enrollment_period_offerings_study_plan_id_fkey FOREIGN KEY (study_plan_id) REFERENCES study_plans(study_plan_id);

ALTER TABLE ONLY enrollment_periods
    ADD CONSTRAINT enrollment_periods_academic_year_fk FOREIGN KEY (academic_year_id) REFERENCES academic_years(academic_year_id);

ALTER TABLE ONLY enrollment_periods
    ADD CONSTRAINT enrollment_periods_institution_fk FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY enrollment_requirement_changes
    ADD CONSTRAINT enrollment_requirement_changes_requirement_id_fkey FOREIGN KEY (requirement_id) REFERENCES enrollment_document_requirements(id);

ALTER TABLE ONLY enrollment_document_requirements
    ADD CONSTRAINT enrollment_requirement_request_tenant_fk FOREIGN KEY (request_id, application_id, institution_id) REFERENCES enrollment_document_requests(id, application_id, institution_id);

ALTER TABLE ONLY users
    ADD CONSTRAINT fk2qqjpih9isqcs22710v8lef9w FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY refresh_tokens
    ADD CONSTRAINT fk3oc3y1jbad69c3h7y2fv88hsu FOREIGN KEY (session_id) REFERENCES user_sessions(user_session_id);

ALTER TABLE ONLY provinces
    ADD CONSTRAINT fk48p9qkti5auert2gquvn76338 FOREIGN KEY (country_id) REFERENCES countries(country_id);

ALTER TABLE ONLY institutions
    ADD CONSTRAINT fk4taatjk5unh4y8ep3tx5ivk60 FOREIGN KEY (city_id) REFERENCES cities(city_id);

ALTER TABLE ONLY platform_refresh_tokens
    ADD CONSTRAINT fk5xgv6h6r8yxl00k576xk80gwe FOREIGN KEY (platform_account_id) REFERENCES platform_accounts(platform_account_id);

ALTER TABLE ONLY user_sessions
    ADD CONSTRAINT fk8klxsgb8dcjjklmqebqp1twd5 FOREIGN KEY (user_id) REFERENCES users(user_id);

ALTER TABLE ONLY people
    ADD CONSTRAINT fk8woik45b2i7oya1sapndmkvsl FOREIGN KEY (birth_city_id) REFERENCES cities(city_id);

ALTER TABLE ONLY addresses
    ADD CONSTRAINT fk9fkb8qaj71tiyr9htkmn7r8y5 FOREIGN KEY (city_id) REFERENCES cities(city_id);

ALTER TABLE ONLY addresses
    ADD CONSTRAINT fk9matrr71ro8s4ujmw1qjn40h4 FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY students
    ADD CONSTRAINT fk9x7brwurtag62dg4e4eugfaf0 FOREIGN KEY (person_id) REFERENCES people(person_id);

ALTER TABLE ONLY study_plan_space_instruments
    ADD CONSTRAINT fk_study_plan_space_instruments_institution FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY study_plan_space_instruments
    ADD CONSTRAINT fk_study_plan_space_instruments_instrument FOREIGN KEY (instrument_id) REFERENCES instruments(instrument_id);

ALTER TABLE ONLY study_plan_space_instruments
    ADD CONSTRAINT fk_study_plan_space_instruments_space FOREIGN KEY (study_plan_space_id) REFERENCES study_plan_spaces(study_plan_space_id);

ALTER TABLE ONLY person_role_assignments
    ADD CONSTRAINT fkboh25xpjxx116ql4n9bje6r6n FOREIGN KEY (person_id) REFERENCES people(person_id);

ALTER TABLE ONLY people
    ADD CONSTRAINT fkbueucqaikoa3yawsdv8pnkn8p FOREIGN KEY (address_id) REFERENCES addresses(address_id);

ALTER TABLE ONLY platform_account_roles
    ADD CONSTRAINT fkbxc242eybs403wkw6tkk6acbe FOREIGN KEY (role_id) REFERENCES roles(role_id);

ALTER TABLE ONLY person_role_assignments
    ADD CONSTRAINT fkc990551153i07vg1j5ksdho1t FOREIGN KEY (role_id) REFERENCES roles(role_id);

ALTER TABLE ONLY cities
    ADD CONSTRAINT fkcf2ndxcsekl26rrkb9egbhq20 FOREIGN KEY (province_id) REFERENCES provinces(province_id);

ALTER TABLE ONLY role_permissions
    ADD CONSTRAINT fkegdk29eiy7mdtefy5c7eirr6e FOREIGN KEY (permission_id) REFERENCES permissions(permission_id);

ALTER TABLE ONLY people
    ADD CONSTRAINT fkfgb64ovhsn54jwubn8qrnkcje FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY platform_account_roles
    ADD CONSTRAINT fkgtyk6jl11gg345lawlp7ym5h9 FOREIGN KEY (platform_account_id) REFERENCES platform_accounts(platform_account_id);

ALTER TABLE ONLY students
    ADD CONSTRAINT fkhu5lxwgdn4ab5bf49h5s10iu5 FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY roles
    ADD CONSTRAINT fkih889rqx3pwxjw7q6x4c08rws FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY platform_refresh_tokens
    ADD CONSTRAINT fkj420c163sgb2ibvr9bgx3kssa FOREIGN KEY (platform_session_id) REFERENCES platform_sessions(platform_session_id);

ALTER TABLE ONLY people
    ADD CONSTRAINT fkmot2mhshfpnt7ph5j31n4kfr3 FOREIGN KEY (nationality_country_id) REFERENCES countries(country_id);

ALTER TABLE ONLY role_permissions
    ADD CONSTRAINT fkn5fotdgk8d1xvo8nav9uv3muc FOREIGN KEY (role_id) REFERENCES roles(role_id);

ALTER TABLE ONLY person_role_assignments
    ADD CONSTRAINT fksjud3l0j0dgr5sxn2b2oivca8 FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY platform_sessions
    ADD CONSTRAINT fkstonfkigy75f40c8tdy53g3a6 FOREIGN KEY (platform_account_id) REFERENCES platform_accounts(platform_account_id);

ALTER TABLE ONLY users
    ADD CONSTRAINT fksv7wp99d6g5x8iisfpjf6sbpg FOREIGN KEY (person_id) REFERENCES people(person_id);

ALTER TABLE ONLY institution_enrollment_locks
    ADD CONSTRAINT institution_enrollment_locks_institution_fk FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY institutional_email_verification_tokens
    ADD CONSTRAINT institutional_email_verification_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(user_id);

ALTER TABLE ONLY institutional_password_reset_tokens
    ADD CONSTRAINT institutional_password_reset_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;

ALTER TABLE ONLY instruments
    ADD CONSTRAINT instruments_institution_fk FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY passkey_credentials
    ADD CONSTRAINT passkey_credentials_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;

ALTER TABLE ONLY people
    ADD CONSTRAINT people_address_institution_fk FOREIGN KEY (institution_id, address_id) REFERENCES addresses(institution_id, address_id);

ALTER TABLE ONLY person_access_invitations
    ADD CONSTRAINT person_access_invitation_institutional_issuer_fk FOREIGN KEY (issuing_institution_id, institutional_issuer_person_id) REFERENCES people(institution_id, person_id);

ALTER TABLE ONLY person_access_invitations
    ADD CONSTRAINT person_access_invitation_institutional_subject_fk FOREIGN KEY (issuing_institution_id, person_id) REFERENCES people(institution_id, person_id);

ALTER TABLE ONLY person_access_invitations
    ADD CONSTRAINT person_access_invitation_subject_fk FOREIGN KEY (representation_id, person_id) REFERENCES person_guardians(person_guardian_id, dependent_person_id);

ALTER TABLE ONLY person_access_invitations
    ADD CONSTRAINT person_access_invitations_person_id_fkey FOREIGN KEY (person_id) REFERENCES people(person_id);

ALTER TABLE ONLY person_access_invitations
    ADD CONSTRAINT person_access_invitations_platform_issuer_account_id_fkey FOREIGN KEY (platform_issuer_account_id) REFERENCES platform_accounts(platform_account_id);

ALTER TABLE ONLY person_access_invitations
    ADD CONSTRAINT person_access_invitations_representation_id_fkey FOREIGN KEY (representation_id) REFERENCES person_guardians(person_guardian_id);

ALTER TABLE ONLY person_guardian_attachments
    ADD CONSTRAINT person_guardian_attachments_institution_fk FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY person_guardian_attachments
    ADD CONSTRAINT person_guardian_attachments_link_institution_fk FOREIGN KEY (institution_id, person_guardian_id) REFERENCES person_guardians(institution_id, person_guardian_id);

ALTER TABLE ONLY person_guardians
    ADD CONSTRAINT person_guardians_declared_nationality_country_id_fkey FOREIGN KEY (declared_nationality_country_id) REFERENCES countries(country_id);

ALTER TABLE ONLY person_guardians
    ADD CONSTRAINT person_guardians_dependent_institution_fk FOREIGN KEY (institution_id, dependent_person_id) REFERENCES people(institution_id, person_id);

ALTER TABLE ONLY person_guardians
    ADD CONSTRAINT person_guardians_resolved_by_institution_fk FOREIGN KEY (institution_id, resolved_by_person_id) REFERENCES people(institution_id, person_id);

ALTER TABLE ONLY person_guardians
    ADD CONSTRAINT person_guardians_tutor_institution_fk FOREIGN KEY (institution_id, tutor_person_id) REFERENCES people(institution_id, person_id);

ALTER TABLE ONLY person_role_assignment_training_paths
    ADD CONSTRAINT person_role_assignment_training__person_role_assignment_id_fkey FOREIGN KEY (person_role_assignment_id) REFERENCES person_role_assignments(person_role_assignment_id) ON DELETE CASCADE;

ALTER TABLE ONLY person_role_assignment_training_paths
    ADD CONSTRAINT person_role_assignment_training_paths_training_path_id_fkey FOREIGN KEY (training_path_id) REFERENCES training_paths(training_path_id);

ALTER TABLE ONLY person_role_assignments
    ADD CONSTRAINT person_role_assignments_person_institution_fk FOREIGN KEY (institution_id, person_id) REFERENCES people(institution_id, person_id);

ALTER TABLE ONLY person_role_assignments
    ADD CONSTRAINT person_role_assignments_role_institution_fk FOREIGN KEY (institution_id, role_id) REFERENCES roles(institution_id, role_id);

ALTER TABLE ONLY platform_email_change_tokens
    ADD CONSTRAINT platform_email_change_account_fk FOREIGN KEY (platform_account_id) REFERENCES platform_accounts(platform_account_id);

ALTER TABLE ONLY platform_passkey_credentials
    ADD CONSTRAINT platform_passkey_credentials_platform_account_id_fkey FOREIGN KEY (platform_account_id) REFERENCES platform_accounts(platform_account_id) ON DELETE CASCADE;

ALTER TABLE ONLY prerequisites
    ADD CONSTRAINT prerequisites_required_plan_fk FOREIGN KEY (study_plan_id, required_study_plan_space_id) REFERENCES study_plan_spaces(study_plan_id, study_plan_space_id);

ALTER TABLE ONLY prerequisites
    ADD CONSTRAINT prerequisites_study_plan_fk FOREIGN KEY (study_plan_id) REFERENCES study_plans(study_plan_id);

ALTER TABLE ONLY prerequisites
    ADD CONSTRAINT prerequisites_target_plan_fk FOREIGN KEY (study_plan_id, target_study_plan_space_id) REFERENCES study_plan_spaces(study_plan_id, study_plan_space_id);

ALTER TABLE ONLY person_role_assignment_training_paths
    ADD CONSTRAINT role_scope_assignment_tenant_fk FOREIGN KEY (institution_id, person_role_assignment_id) REFERENCES person_role_assignments(institution_id, person_role_assignment_id) ON DELETE CASCADE;

ALTER TABLE ONLY person_role_assignment_training_paths
    ADD CONSTRAINT role_scope_training_path_tenant_fk FOREIGN KEY (institution_id, training_path_id) REFERENCES training_paths(institution_id, training_path_id);

ALTER TABLE ONLY shifts
    ADD CONSTRAINT shifts_institution_fk FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY students
    ADD CONSTRAINT students_person_institution_fk FOREIGN KEY (institution_id, person_id) REFERENCES people(institution_id, person_id);

ALTER TABLE ONLY study_plan_spaces
    ADD CONSTRAINT study_plan_spaces_level_plan_fk FOREIGN KEY (study_plan_id, academic_level_id) REFERENCES academic_levels(study_plan_id, academic_level_id);

ALTER TABLE ONLY study_plan_spaces
    ADD CONSTRAINT study_plan_spaces_plan_institution_fk FOREIGN KEY (institution_id, study_plan_id) REFERENCES study_plans(institution_id, study_plan_id);

ALTER TABLE ONLY study_plan_spaces
    ADD CONSTRAINT study_plan_spaces_space_institution_fk FOREIGN KEY (institution_id, academic_space_id) REFERENCES academic_spaces(institution_id, academic_space_id);

ALTER TABLE ONLY study_plans
    ADD CONSTRAINT study_plans_institution_fk FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY study_plans
    ADD CONSTRAINT study_plans_previous_version_institution_fk FOREIGN KEY (institution_id, previous_version_id) REFERENCES study_plans(institution_id, study_plan_id);

ALTER TABLE ONLY study_plans
    ADD CONSTRAINT study_plans_training_path_institution_fk FOREIGN KEY (institution_id, training_path_id) REFERENCES training_paths(institution_id, training_path_id);

ALTER TABLE ONLY training_path_document_requirements
    ADD CONSTRAINT training_path_document_path_tenant_fk FOREIGN KEY (institution_id, training_path_id) REFERENCES training_paths(institution_id, training_path_id);

ALTER TABLE ONLY training_path_document_requirements
    ADD CONSTRAINT training_path_document_requirements_training_path_id_fkey FOREIGN KEY (training_path_id) REFERENCES training_paths(training_path_id);

ALTER TABLE ONLY training_path_document_requirements
    ADD CONSTRAINT training_path_document_tenant_fk FOREIGN KEY (institution_id, document_id) REFERENCES document_definitions(institution_id, id);

ALTER TABLE ONLY training_paths
    ADD CONSTRAINT training_paths_institution_fk FOREIGN KEY (institution_id) REFERENCES institutions(institution_id);

ALTER TABLE ONLY user_sessions
    ADD CONSTRAINT user_sessions_active_person_fk FOREIGN KEY (active_person_id) REFERENCES people(person_id);

ALTER TABLE ONLY users
    ADD CONSTRAINT users_person_institution_fk FOREIGN KEY (institution_id, person_id) REFERENCES people(institution_id, person_id);

-- Permission catalog.
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.544967+00', '2026-11-12 01:28:36.544967+00', 'c6bc8bd5-e02e-ed8c-9488-01811356c442', 'INSTITUTION', 'institution:academic-year:read', 'Ver ciclos lectivos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.544967+00', '2026-11-12 01:28:36.544967+00', 'f1e25d89-e329-2d37-8df1-d3213d97bc50', 'INSTITUTION', 'institution:academic-year:create', 'Crear ciclos lectivos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.544967+00', '2026-11-12 01:28:36.544967+00', '62ad8260-2d0c-6146-1367-4858726e3e88', 'INSTITUTION', 'institution:academic-year:update', 'Editar ciclos lectivos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.544967+00', '2026-11-12 01:28:36.544967+00', '70269e13-94f8-cc52-eeb3-2dc002977b3b', 'INSTITUTION', 'institution:academic-year:update-status', 'Cambiar estado de ciclos lectivos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.544967+00', '2026-11-12 01:28:36.544967+00', '70edce7b-39c9-0ad9-ef95-cd35db75fcf3', 'INSTITUTION', 'institution:training-path:read', 'Ver trayectos formativos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.544967+00', '2026-11-12 01:28:36.544967+00', '1bbafd69-b017-0e66-3ddc-d3c5bdb14194', 'INSTITUTION', 'institution:training-path:create', 'Crear trayectos formativos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.544967+00', '2026-11-12 01:28:36.544967+00', '115eb737-1829-5f36-1cf8-27e0afcb5e99', 'INSTITUTION', 'institution:training-path:update', 'Editar trayectos formativos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.544967+00', '2026-11-12 01:28:36.544967+00', 'e7b5b64f-b635-3485-2aa8-897ab35e7959', 'INSTITUTION', 'institution:study-plan:read', 'Ver planes de estudio');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.544967+00', '2026-11-12 01:28:36.544967+00', '88163819-5d91-33b0-0576-a047d7a70d82', 'INSTITUTION', 'institution:study-plan:create', 'Crear planes de estudio');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.544967+00', '2026-11-12 01:28:36.544967+00', 'f67609d0-2a30-0e2a-dd46-3080ffbe7c17', 'INSTITUTION', 'institution:study-plan:update', 'Editar planes de estudio');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.544967+00', '2026-11-12 01:28:36.544967+00', 'd9e95b37-a906-a507-bfc3-22ac2582bfa8', 'INSTITUTION', 'institution:study-plan:curriculum-update', 'Editar estructura curricular');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.544967+00', '2026-11-12 01:28:36.544967+00', 'dea18932-815a-3c09-8b62-219f8544ec48', 'INSTITUTION', 'institution:academic-space:read', 'Ver espacios académicos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.544967+00', '2026-11-12 01:28:36.544967+00', '829b9a3e-ed63-1694-6f52-63db908a0f1a', 'INSTITUTION', 'institution:academic-space:create', 'Crear espacios académicos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.544967+00', '2026-11-12 01:28:36.544967+00', '1bd36c48-4d03-321d-0501-3d1b4d301697', 'INSTITUTION', 'institution:academic-space:update', 'Editar espacios académicos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.544967+00', '2026-11-12 01:28:36.544967+00', '4c5eaafd-4a05-80b5-8fc6-6afd70e4bcd3', 'INSTITUTION', 'institution:instrument:read', 'Ver instrumentos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.544967+00', '2026-11-12 01:28:36.544967+00', '0a87000b-4077-2983-79c7-7e120047ac7e', 'INSTITUTION', 'institution:instrument:create', 'Crear instrumentos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.544967+00', '2026-11-12 01:28:36.544967+00', '23d63f6e-8737-a775-ac7e-b82bed510eb9', 'INSTITUTION', 'institution:instrument:update', 'Editar instrumentos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.544967+00', '2026-11-12 01:28:36.599024+00', '1a77b525-2859-c6c9-a225-c5b6563be7c2', 'INSTITUTION', 'institution:training-path:update-status', 'Activar o desactivar trayectos formativos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.544967+00', '2026-11-12 01:28:36.599024+00', '632a5ce8-5be0-eca5-4b25-88559abb81d0', 'INSTITUTION', 'institution:study-plan:update-status', 'Activar o desactivar planes de estudio');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.544967+00', '2026-11-12 01:28:36.599024+00', 'c4f04655-23de-4676-8467-7f4d01103ec2', 'INSTITUTION', 'institution:academic-space:update-status', 'Activar o desactivar espacios académicos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.544967+00', '2026-11-12 01:28:36.599024+00', 'cc3ddeb0-0d5c-a794-4aaf-eb259824ddd5', 'INSTITUTION', 'institution:instrument:update-status', 'Activar o desactivar instrumentos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.701281+00', '2026-11-12 01:28:36.701281+00', '2bd2289b-98b7-23f3-4a5b-421136e7a955', 'INSTITUTION', 'institution:academic-year:delete', 'Eliminar ciclos lectivos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.701281+00', '2026-11-12 01:28:36.701281+00', 'd682be50-fdbc-780e-d400-4639ab6389d3', 'INSTITUTION', 'institution:academic-year:restore', 'Restaurar ciclos lectivos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.701281+00', '2026-11-12 01:28:36.701281+00', '1a19fccd-8732-7a93-29e1-4b91c857df2b', 'INSTITUTION', 'institution:training-path:delete', 'Eliminar trayectos formativos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.701281+00', '2026-11-12 01:28:36.701281+00', '280d38ca-527e-aa15-501b-c4b308f87f61', 'INSTITUTION', 'institution:training-path:restore', 'Restaurar trayectos formativos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.701281+00', '2026-11-12 01:28:36.701281+00', 'cfeff4d6-01c5-457d-516d-11a0c476ed2a', 'INSTITUTION', 'institution:study-plan:delete', 'Eliminar planes de estudio');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.701281+00', '2026-11-12 01:28:36.701281+00', '037a54d7-c703-ea0f-4b17-5e69b9994b32', 'INSTITUTION', 'institution:study-plan:restore', 'Restaurar planes de estudio');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.701281+00', '2026-11-12 01:28:36.701281+00', '5ee1ecc1-c6ea-15a6-b7eb-9e472c8762d6', 'INSTITUTION', 'institution:academic-space:delete', 'Eliminar espacios académicos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.701281+00', '2026-11-12 01:28:36.701281+00', 'd8880193-7146-84da-e181-9339174466bb', 'INSTITUTION', 'institution:academic-space:restore', 'Restaurar espacios académicos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.701281+00', '2026-11-12 01:28:36.701281+00', 'd49382c7-e443-e87c-b00a-81271bd26e38', 'INSTITUTION', 'institution:instrument:delete', 'Eliminar instrumentos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:36.701281+00', '2026-11-12 01:28:36.701281+00', 'ddde2bb8-dca2-1639-3838-9a5d54e011ef', 'INSTITUTION', 'institution:instrument:restore', 'Restaurar instrumentos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:37.003767+00', '2026-11-12 01:28:37.003767+00', 'eef67acb-8023-8088-fdd9-ee47785fedd9', 'INSTITUTION', 'institution:shift:read', 'Ver turnos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:37.003767+00', '2026-11-12 01:28:37.003767+00', 'c72ea326-3302-9897-2022-93ef22bf82e5', 'INSTITUTION', 'institution:shift:create', 'Crear turnos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:37.003767+00', '2026-11-12 01:28:37.003767+00', '03f44cc4-2264-a132-1add-fb7537000f01', 'INSTITUTION', 'institution:shift:update', 'Editar turnos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:37.003767+00', '2026-11-12 01:28:37.003767+00', '0e00a151-37ec-c5ac-0aca-ff7f2f9bd0fa', 'INSTITUTION', 'institution:shift:update-status', 'Activar o desactivar turnos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:37.003767+00', '2026-11-12 01:28:37.003767+00', '2256031b-cf55-3a55-f8aa-ae08202b2cfd', 'INSTITUTION', 'institution:shift:delete', 'Eliminar turnos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:37.003767+00', '2026-11-12 01:28:37.003767+00', 'ae0e0158-e7eb-f509-dfb4-03c273a2ebfe', 'INSTITUTION', 'institution:shift:restore', 'Restaurar turnos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:37.220926+00', '2026-11-12 01:28:37.220926+00', '6ee5214d-6f5a-e0fa-61df-edfd4857d65c', 'INSTITUTION', 'institution:course:read', 'Ver cursos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:37.220926+00', '2026-11-12 01:28:37.220926+00', 'f442f66b-bc0e-1353-244c-53cd2425a3ea', 'INSTITUTION', 'institution:course:create', 'Crear cursos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:37.220926+00', '2026-11-12 01:28:37.220926+00', 'd9ea31c7-5281-0cad-ab67-88b30a200a1e', 'INSTITUTION', 'institution:course:update', 'Editar cursos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:37.220926+00', '2026-11-12 01:28:37.220926+00', 'bb37b89d-869f-f9be-e86c-00532a9ad61b', 'INSTITUTION', 'institution:course:update-status', 'Activar o desactivar cursos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:37.220926+00', '2026-11-12 01:28:37.220926+00', 'b0c54d75-c950-ccab-13f5-91017e1baedc', 'INSTITUTION', 'institution:course:delete', 'Eliminar cursos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:37.220926+00', '2026-11-12 01:28:37.220926+00', '077a946a-097f-2c60-332c-74ad061ed1fc', 'INSTITUTION', 'institution:course:restore', 'Restaurar cursos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:37.70791+00', '2026-11-12 01:28:37.70791+00', '9b462b68-6146-6133-e05f-f9d0db3bf5d4', 'INSTITUTION', 'institution:academic-offer:read', 'Consultar oferta académica disponible');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:38.355396+00', '2026-11-12 01:28:38.355396+00', '9588bdb2-f884-a008-cca8-3159e1d21fa7', 'INSTITUTION', 'institution:enrollment-application:read', 'Ver solicitudes de inscripción');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:38.355396+00', '2026-11-12 01:28:38.355396+00', 'cff2dc2e-4155-11c6-1996-8305be46bd0e', 'INSTITUTION', 'institution:enrollment-application:approve', 'Aprobar solicitudes de inscripción');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:38.355396+00', '2026-11-12 01:28:38.355396+00', '6a6ee37b-45dd-6533-68ad-cec5f70321fb', 'INSTITUTION', 'institution:enrollment-application:reject', 'Rechazar solicitudes de inscripción');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:38.886734+00', '2026-11-12 01:28:38.886734+00', '27230853-e299-cecb-ce1a-073b3c89ae09', 'INSTITUTION', 'institution:course-enrollment:read', 'Ver cursadas institucionales');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:38.886734+00', '2026-11-12 01:28:38.886734+00', 'eb3f5e73-0b0d-179c-9073-15fbdda0a753', 'INSTITUTION', 'institution:course-enrollment:create', 'Crear cursadas manuales');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:38.886734+00', '2026-11-12 01:28:38.886734+00', 'a02bc0ec-72fb-6f94-5313-07d5c7649489', 'INSTITUTION', 'institution:course-enrollment:withdraw', 'Registrar bajas de cursadas');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:38.886734+00', '2026-11-12 01:28:38.886734+00', 'c836d2c9-bf4d-47d7-6cb9-05ff9c99e59e', 'INSTITUTION', 'institution:course-enrollment:academic-status-update', 'Modificar resultados académicos');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:38.886734+00', '2026-11-12 01:28:38.886734+00', '85421f36-d415-c809-4b76-4475d66e566b', 'INSTITUTION', 'institution:enrollment-application-course:read', 'Ver solicitudes de cursada');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:38.886734+00', '2026-11-12 01:28:38.886734+00', '94ada7dc-b3af-04e1-7283-0c5ae288e974', 'INSTITUTION', 'institution:enrollment-application-course:enroll', 'Inscribir solicitudes de cursada');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:38.886734+00', '2026-11-12 01:28:38.886734+00', '815043b1-6875-c1c5-5662-0a69ecf1a2d3', 'INSTITUTION', 'institution:enrollment-application-course:reject', 'Rechazar solicitudes de cursada');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:38.886734+00', '2026-11-12 01:28:38.886734+00', '74c3ed62-9f97-f981-2561-804cbe5a3efb', 'INSTITUTION', 'institution:course-waitlist:read', 'Ver listas de espera');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:42.259551+00', '2026-11-12 01:28:42.259551+00', '5e0d974c-1fa0-4dd4-a029-026f43232973', 'INSTITUTION', 'institution:own-enrollment-application:read', 'Consultar mis solicitudes de inscripción');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:42.259551+00', '2026-11-12 01:28:42.259551+00', '0d66cca0-b572-1d33-5a3b-92a605cdf965', 'INSTITUTION', 'institution:own-enrollment-application:manage', 'Crear, modificar y enviar mis solicitudes de inscripción');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:42.259551+00', '2026-11-12 01:28:42.259551+00', '946e2718-3a6e-6fb2-514e-e4f0c7a018d4', 'INSTITUTION', 'institution:own-formation:read', 'Consultar mis materias y horarios');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:42.259551+00', '2026-11-12 01:28:42.259551+00', 'a00ce1b0-7053-7182-0b62-3957b2a20411', 'INSTITUTION', 'institution:teaching:read', 'Consultar mis clases, horarios y estudiantes');
INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description) VALUES ('2026-11-12 01:28:42.259551+00', '2026-11-12 01:28:42.259551+00', '8f80e36b-261e-db35-c417-ae4f99918a15', 'INSTITUTION', 'institution:teaching:academic-status-update', 'Modificar resultados académicos de mis clases');
