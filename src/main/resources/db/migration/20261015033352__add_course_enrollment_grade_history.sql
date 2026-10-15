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
        CHECK (operation IN ('CREATED', 'UPDATED', 'MARKED_FOR_DELETION', 'CANCELLED_DELETION', 'PUBLISHED', 'DELETED_DRAFT', 'DELETED_PUBLISHED', 'BASELINE')),
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
-- gradeId is kept as a stable logical reference (course_enrollment_grade_id).

CREATE INDEX course_enrollment_grade_histories_lookup_idx
    ON course_enrollment_grade_histories (institution_id, course_enrollment_id, course_enrollment_grade_id, changed_at DESC, course_enrollment_grade_history_id DESC);

CREATE INDEX course_enrollment_grade_histories_enrollment_changed_idx
    ON course_enrollment_grade_histories (institution_id, course_enrollment_id, changed_at DESC);

-- Baseline for grades existing before this migration: capture known state without
-- inventing past modifications, authors or dates. changed_at marks when
-- traceability starts. Empty grades table inserts zero rows.
INSERT INTO course_enrollment_grade_histories
    (course_enrollment_grade_history_id, institution_id, course_enrollment_id, course_enrollment_grade_id,
     operation, evaluation_before, value_before, evaluation_after, value_after,
     published_evaluation_before, published_value_before, published_evaluation_after, published_value_after,
     previous_status, new_status, actor_person_id, changed_at, created_at, updated_at)
SELECT gen_random_uuid(), grade.institution_id, grade.course_enrollment_id, grade.course_enrollment_grade_id,
    'BASELINE', NULL, NULL, grade.evaluation, grade.value,
    NULL, NULL, grade.published_evaluation, grade.published_value,
    NULL,
    CASE
        WHEN grade.pending_deletion THEN 'PENDING_DELETION'
        WHEN grade.published_evaluation IS NULL THEN 'DRAFT'
        WHEN grade.evaluation <> grade.published_evaluation OR grade.value <> grade.published_value THEN 'PENDING_CHANGES'
        ELSE 'PUBLISHED'
    END,
    NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM course_enrollment_grades grade;
