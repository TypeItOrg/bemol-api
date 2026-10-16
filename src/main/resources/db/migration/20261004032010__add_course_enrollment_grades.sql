CREATE TABLE course_enrollment_grades (
    course_enrollment_grade_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    course_enrollment_id uuid NOT NULL,
    evaluation varchar(150) NOT NULL,
    value numeric(4, 2) NOT NULL,
    published_evaluation varchar(150),
    published_value numeric(4, 2),
    pending_deletion boolean NOT NULL DEFAULT false,
    created_by_person_id uuid,
    updated_by_person_id uuid,
    published_by_person_id uuid,
    published_at timestamptz(6),
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz(6) NOT NULL,
    updated_at timestamptz(6) NOT NULL,
    CONSTRAINT course_enrollment_grades_pkey PRIMARY KEY (course_enrollment_grade_id),
    CONSTRAINT course_enrollment_grades_tenant_id_unique UNIQUE (institution_id, course_enrollment_grade_id),
    CONSTRAINT course_enrollment_grades_enrollment_fk
        FOREIGN KEY (institution_id, course_enrollment_id)
        REFERENCES course_enrollments (institution_id, course_enrollment_id),
    CONSTRAINT course_enrollment_grades_created_by_fk
        FOREIGN KEY (created_by_person_id) REFERENCES people (person_id),
    CONSTRAINT course_enrollment_grades_updated_by_fk
        FOREIGN KEY (updated_by_person_id) REFERENCES people (person_id),
    CONSTRAINT course_enrollment_grades_published_by_fk
        FOREIGN KEY (published_by_person_id) REFERENCES people (person_id),
    CONSTRAINT course_enrollment_grades_evaluation_check CHECK (btrim(evaluation) <> ''),
    CONSTRAINT course_enrollment_grades_published_evaluation_check
        CHECK (published_evaluation IS NULL OR btrim(published_evaluation) <> ''),
    CONSTRAINT course_enrollment_grades_value_range_check CHECK (value >= 1 AND value <= 10),
    CONSTRAINT course_enrollment_grades_published_value_range_check
        CHECK (published_value IS NULL OR (published_value >= 1 AND published_value <= 10)),
    CONSTRAINT course_enrollment_grades_published_pair_check
        CHECK ((published_evaluation IS NULL) = (published_value IS NULL)),
    CONSTRAINT course_enrollment_grades_pending_deletion_check
        CHECK (pending_deletion = false OR published_evaluation IS NOT NULL)
);

CREATE UNIQUE INDEX course_enrollment_grades_enrollment_evaluation_unique
    ON course_enrollment_grades (course_enrollment_id, lower(btrim(evaluation)));

CREATE INDEX course_enrollment_grades_enrollment_idx
    ON course_enrollment_grades (institution_id, course_enrollment_id);

CREATE INDEX course_enrollment_grades_pending_idx
    ON course_enrollment_grades (institution_id, course_enrollment_id)
    WHERE pending_deletion OR published_evaluation IS NULL;

INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description)
VALUES
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, md5('institution:course-enrollment-grade:read')::uuid, 'INSTITUTION', 'institution:course-enrollment-grade:read', 'Consultar notas de estudiantes'),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, md5('institution:course-enrollment-grade:create')::uuid, 'INSTITUTION', 'institution:course-enrollment-grade:create', 'Añadir notas de estudiantes'),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, md5('institution:course-enrollment-grade:update')::uuid, 'INSTITUTION', 'institution:course-enrollment-grade:update', 'Modificar notas de estudiantes'),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, md5('institution:course-enrollment-grade:delete')::uuid, 'INSTITUTION', 'institution:course-enrollment-grade:delete', 'Eliminar notas de estudiantes'),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, md5('institution:course-enrollment-grade:publish')::uuid, 'INSTITUTION', 'institution:course-enrollment-grade:publish', 'Publicar notas de estudiantes')
ON CONFLICT (code) DO UPDATE
SET description = EXCLUDED.description,
    scope = EXCLUDED.scope,
    updated_at = CURRENT_TIMESTAMP;

DELETE FROM role_permissions
WHERE permission_id IN (
    SELECT permission_id FROM permissions
    WHERE code IN ('institution:grades:enter', 'institution:grades:enter-final')
);

DELETE FROM permissions
WHERE code IN ('institution:grades:enter', 'institution:grades:enter-final');
