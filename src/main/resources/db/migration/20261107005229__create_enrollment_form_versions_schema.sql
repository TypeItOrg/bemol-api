CREATE TABLE enrollment_forms (
    id uuid PRIMARY KEY,
    institution_id uuid NOT NULL REFERENCES institutions(institution_id),
    name varchar(150) NOT NULL,
    revision bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT enrollment_forms_tenant_unique UNIQUE (institution_id, id),
    CONSTRAINT enrollment_forms_name_check CHECK (name ~ '[^[:space:]]'),
    CONSTRAINT enrollment_forms_revision_check CHECK (revision >= 0)
);

CREATE TABLE enrollment_form_versions (
    id uuid PRIMARY KEY,
    institution_id uuid NOT NULL REFERENCES institutions(institution_id),
    form_id uuid NOT NULL,
    version_number integer NOT NULL,
    status varchar(20) NOT NULL,
    definition jsonb NOT NULL,
    revision bigint NOT NULL DEFAULT 0,
    published_at timestamptz,
    archived_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT enrollment_form_versions_tenant_unique UNIQUE (institution_id, id),
    CONSTRAINT enrollment_form_versions_number_unique UNIQUE (form_id, version_number),
    CONSTRAINT enrollment_form_versions_form_tenant_fk
        FOREIGN KEY (institution_id, form_id) REFERENCES enrollment_forms(institution_id, id),
    CONSTRAINT enrollment_form_versions_number_check CHECK (version_number > 0),
    CONSTRAINT enrollment_form_versions_revision_check CHECK (revision >= 0),
    CONSTRAINT enrollment_form_versions_definition_check CHECK (jsonb_typeof(definition) = 'object'),
    CONSTRAINT enrollment_form_versions_state_check CHECK (
        (status = 'DRAFT' AND published_at IS NULL AND archived_at IS NULL)
        OR (status = 'PUBLISHED' AND published_at IS NOT NULL AND archived_at IS NULL)
        OR (status = 'ARCHIVED' AND published_at IS NOT NULL AND archived_at IS NOT NULL
            AND archived_at >= published_at)
    )
);

CREATE UNIQUE INDEX enrollment_form_versions_draft_unique
    ON enrollment_form_versions(form_id) WHERE status = 'DRAFT';

CREATE TABLE enrollment_form_assignments (
    id uuid PRIMARY KEY,
    institution_id uuid NOT NULL REFERENCES institutions(institution_id),
    training_path_id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    form_version_id uuid NOT NULL,
    revision bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT enrollment_form_assignments_tenant_unique UNIQUE (institution_id, id),
    CONSTRAINT enrollment_form_assignments_context_unique
        UNIQUE (institution_id, training_path_id, academic_year_id),
    CONSTRAINT enrollment_form_assignments_path_tenant_fk
        FOREIGN KEY (institution_id, training_path_id)
        REFERENCES training_paths(institution_id, training_path_id),
    CONSTRAINT enrollment_form_assignments_year_tenant_fk
        FOREIGN KEY (institution_id, academic_year_id)
        REFERENCES academic_years(institution_id, academic_year_id),
    CONSTRAINT enrollment_form_assignments_version_tenant_fk
        FOREIGN KEY (institution_id, form_version_id)
        REFERENCES enrollment_form_versions(institution_id, id),
    CONSTRAINT enrollment_form_assignments_revision_check CHECK (revision >= 0)
);

CREATE INDEX enrollment_form_assignments_version_idx ON enrollment_form_assignments(form_version_id);

-- Null remains the fixed BASE_V1 contract, including existing drafts.
ALTER TABLE enrollment_applications
    ADD COLUMN form_version_id uuid,
    ADD COLUMN custom_answers jsonb NOT NULL DEFAULT '{}',
    ADD COLUMN draft_revision bigint NOT NULL DEFAULT 0,
    ADD CONSTRAINT enrollment_application_form_version_tenant_fk
        FOREIGN KEY (institution_id, form_version_id)
        REFERENCES enrollment_form_versions(institution_id, id),
    ADD CONSTRAINT enrollment_application_custom_answers_check
        CHECK (jsonb_typeof(custom_answers) = 'object'),
    ADD CONSTRAINT enrollment_application_base_answers_check
        CHECK (form_version_id IS NOT NULL OR custom_answers = '{}'::jsonb),
    ADD CONSTRAINT enrollment_application_draft_revision_check CHECK (draft_revision >= 0);

CREATE INDEX enrollment_applications_form_version_idx ON enrollment_applications(form_version_id)
    WHERE form_version_id IS NOT NULL;

-- Hibernate's PostgreSQL extractor reads constraint names from the error text.
CREATE FUNCTION protect_enrollment_form_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.institution_id IS DISTINCT FROM OLD.institution_id THEN
        RAISE EXCEPTION 'Enrollment form identity is immutable (constraint "enrollment_forms_identity_check")'
            USING ERRCODE = '23514', CONSTRAINT = 'enrollment_forms_identity_check';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER enrollment_form_identity BEFORE UPDATE ON enrollment_forms
    FOR EACH ROW EXECUTE FUNCTION protect_enrollment_form_identity();

CREATE FUNCTION protect_enrollment_form_version() RETURNS trigger LANGUAGE plpgsql AS $$
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

CREATE TRIGGER enrollment_form_version_lifecycle BEFORE INSERT OR UPDATE OR DELETE
    ON enrollment_form_versions FOR EACH ROW EXECUTE FUNCTION protect_enrollment_form_version();

CREATE FUNCTION protect_enrollment_form_assignment() RETURNS trigger LANGUAGE plpgsql AS $$
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

CREATE TRIGGER enrollment_form_assignment_context BEFORE INSERT OR UPDATE ON enrollment_form_assignments
    FOR EACH ROW EXECUTE FUNCTION protect_enrollment_form_assignment();

CREATE FUNCTION protect_enrollment_application_form() RETURNS trigger LANGUAGE plpgsql AS $$
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

CREATE TRIGGER enrollment_application_form BEFORE INSERT OR UPDATE ON enrollment_applications
    FOR EACH ROW EXECUTE FUNCTION protect_enrollment_application_form();
