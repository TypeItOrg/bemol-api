ALTER TABLE course_enrollment_grade_histories
    DROP CONSTRAINT course_enrollment_grade_histories_operation_check;

-- New audited operations for grades discarded when an enrollment leaves ENROLLED:
-- DISCARDED_DRAFT_ON_EXIT (never-published draft physically deleted) and
-- REVERTED_TO_PUBLISHED_ON_EXIT (unpublished working changes / pending deletion rolled back).
ALTER TABLE course_enrollment_grade_histories
    ADD CONSTRAINT course_enrollment_grade_histories_operation_check
        CHECK (operation IN (
            'CREATED', 'UPDATED', 'MARKED_FOR_DELETION', 'CANCELLED_DELETION', 'PUBLISHED',
            'DELETED_DRAFT', 'DELETED_PUBLISHED', 'BASELINE',
            'DISCARDED_DRAFT_ON_EXIT', 'REVERTED_TO_PUBLISHED_ON_EXIT'));
