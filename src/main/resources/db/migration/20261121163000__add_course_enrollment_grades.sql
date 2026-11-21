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
        CHECK (
            (published_evaluation IS NULL) = (published_value IS NULL)
            AND (published_evaluation IS NULL) = (published_at IS NULL)
        ),
    CONSTRAINT course_enrollment_grades_pending_deletion_check
        CHECK (pending_deletion = false OR published_evaluation IS NOT NULL)
);

CREATE UNIQUE INDEX course_enrollment_grades_enrollment_evaluation_unique
    ON course_enrollment_grades (course_enrollment_id, lower(btrim(evaluation)));

CREATE INDEX course_enrollment_grades_enrollment_idx
    ON course_enrollment_grades (institution_id, course_enrollment_id);

CREATE INDEX course_enrollment_grades_pending_idx
    ON course_enrollment_grades (institution_id, course_enrollment_id)
    WHERE pending_deletion
       OR published_evaluation IS NULL
       OR evaluation <> published_evaluation
       OR value <> published_value;

CREATE TABLE course_enrollment_grade_histories (
    course_enrollment_grade_history_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    course_enrollment_id uuid NOT NULL,
    course_enrollment_grade_id uuid NOT NULL,
    operation varchar(30) NOT NULL,
    evaluation_before varchar(150),
    value_before numeric(4, 2),
    evaluation_after varchar(150),
    value_after numeric(4, 2),
    published_evaluation_before varchar(150),
    published_value_before numeric(4, 2),
    published_evaluation_after varchar(150),
    published_value_after numeric(4, 2),
    previous_status varchar(30),
    new_status varchar(30),
    actor_person_id uuid,
    changed_at timestamptz(6) NOT NULL,
    created_at timestamptz(6) NOT NULL,
    updated_at timestamptz(6) NOT NULL,
    CONSTRAINT course_enrollment_grade_histories_pkey PRIMARY KEY (course_enrollment_grade_history_id),
    CONSTRAINT course_enrollment_grade_histories_tenant_id_unique UNIQUE (institution_id, course_enrollment_grade_history_id),
    CONSTRAINT course_enrollment_grade_histories_tenant_enrollment_fk
        FOREIGN KEY (institution_id, course_enrollment_id)
        REFERENCES course_enrollments (institution_id, course_enrollment_id),
    CONSTRAINT course_enrollment_grade_histories_actor_fk
        FOREIGN KEY (actor_person_id) REFERENCES people (person_id),
    CONSTRAINT course_enrollment_grade_histories_operation_check
        CHECK (operation IN (
            'CREATED', 'UPDATED', 'MARKED_FOR_DELETION', 'CANCELLED_DELETION', 'PUBLISHED',
            'DELETED_DRAFT', 'DELETED_PUBLISHED', 'BASELINE',
            'DISCARDED_DRAFT_ON_EXIT', 'REVERTED_TO_PUBLISHED_ON_EXIT')),
    CONSTRAINT course_enrollment_grade_histories_previous_status_check
        CHECK (previous_status IS NULL OR previous_status IN ('DRAFT', 'PUBLISHED', 'PENDING_CHANGES', 'PENDING_DELETION')),
    CONSTRAINT course_enrollment_grade_histories_new_status_check
        CHECK (new_status IS NULL OR new_status IN ('DRAFT', 'PUBLISHED', 'PENDING_CHANGES', 'PENDING_DELETION')),
    CONSTRAINT course_enrollment_grade_histories_evaluation_before_check
        CHECK (evaluation_before IS NULL OR btrim(evaluation_before) <> ''),
    CONSTRAINT course_enrollment_grade_histories_evaluation_after_check
        CHECK (evaluation_after IS NULL OR btrim(evaluation_after) <> ''),
    CONSTRAINT course_enrollment_grade_histories_value_before_range_check
        CHECK (value_before IS NULL OR (value_before >= 1 AND value_before <= 10)),
    CONSTRAINT course_enrollment_grade_histories_value_after_range_check
        CHECK (value_after IS NULL OR (value_after >= 1 AND value_after <= 10)),
    CONSTRAINT course_enrollment_grade_histories_published_evaluation_before_check
        CHECK (published_evaluation_before IS NULL OR btrim(published_evaluation_before) <> ''),
    CONSTRAINT course_enrollment_grade_histories_published_evaluation_after_check
        CHECK (published_evaluation_after IS NULL OR btrim(published_evaluation_after) <> ''),
    CONSTRAINT course_enrollment_grade_histories_published_value_before_range_check
        CHECK (published_value_before IS NULL OR (published_value_before >= 1 AND published_value_before <= 10)),
    CONSTRAINT course_enrollment_grade_histories_published_value_after_range_check
        CHECK (published_value_after IS NULL OR (published_value_after >= 1 AND published_value_after <= 10))
);

-- No FK towards course_enrollment_grades on purpose: history must survive physical
-- deletion of draft grades and effective deletion of published grades on publish.
CREATE INDEX course_enrollment_grade_histories_lookup_idx
    ON course_enrollment_grade_histories (institution_id, course_enrollment_id, course_enrollment_grade_id, changed_at DESC, course_enrollment_grade_history_id DESC);

CREATE INDEX course_enrollment_grade_histories_enrollment_changed_idx
    ON course_enrollment_grade_histories (institution_id, course_enrollment_id, changed_at DESC);

INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description)
VALUES
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, md5('institution:course-enrollment-grade:read')::uuid, 'INSTITUTION', 'institution:course-enrollment-grade:read', 'Consultar notas de estudiantes'),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, md5('institution:course-enrollment-grade:create')::uuid, 'INSTITUTION', 'institution:course-enrollment-grade:create', 'Añadir notas de estudiantes'),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, md5('institution:course-enrollment-grade:update')::uuid, 'INSTITUTION', 'institution:course-enrollment-grade:update', 'Modificar notas de estudiantes'),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, md5('institution:course-enrollment-grade:delete')::uuid, 'INSTITUTION', 'institution:course-enrollment-grade:delete', 'Eliminar notas de estudiantes'),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, md5('institution:course-enrollment-grade:publish')::uuid, 'INSTITUTION', 'institution:course-enrollment-grade:publish', 'Publicar notas de estudiantes'),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, md5('institution:teaching:grade-manage')::uuid, 'INSTITUTION', 'institution:teaching:grade-manage', 'Gestionar y publicar notas de mis clases')
ON CONFLICT (code) DO UPDATE
SET description = EXCLUDED.description,
    scope = EXCLUDED.scope,
    updated_at = CURRENT_TIMESTAMP;

-- Backfill new grade permissions onto existing system roles across institutions.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p
  ON p.code IN (
      'institution:course-enrollment-grade:read',
      'institution:course-enrollment-grade:create',
      'institution:course-enrollment-grade:update',
      'institution:course-enrollment-grade:delete',
      'institution:course-enrollment-grade:publish')
WHERE r.scope = 'INSTITUTION'
  AND r.code IN ('ADMINISTRATIVE', 'INSTITUTIONAL_AUTHORITY')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'institution:teaching:grade-manage'
WHERE r.scope = 'INSTITUTION'
  AND r.code IN ('TEACHER', 'INSTITUTIONAL_AUTHORITY')
ON CONFLICT DO NOTHING;

DELETE FROM role_permissions
WHERE permission_id IN (
    SELECT permission_id FROM permissions
    WHERE code IN ('institution:grades:enter', 'institution:grades:enter-final')
);

DELETE FROM permissions
WHERE code IN ('institution:grades:enter', 'institution:grades:enter-final');
