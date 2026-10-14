-- Recover only unambiguous historical context. Unknown historical rows remain readable.
WITH evidence AS (
    SELECT application.enrollment_application_id, period.academic_year_id
    FROM enrollment_applications application
    JOIN enrollment_periods period ON period.enrollment_period_id = application.enrollment_period_id
    UNION
    SELECT selection.enrollment_application_id, course.academic_year_id
    FROM enrollment_application_courses selection
    JOIN courses course ON course.course_id = selection.course_id
    UNION
    SELECT history.enrollment_application_id, period.academic_year_id
    FROM enrollment_application_periods history
    JOIN enrollment_periods period ON period.enrollment_period_id = history.enrollment_period_id
), resolved AS (
    SELECT enrollment_application_id, (array_agg(DISTINCT academic_year_id))[1] AS academic_year_id
    FROM evidence
    GROUP BY enrollment_application_id
    HAVING count(DISTINCT academic_year_id) = 1
)
UPDATE enrollment_applications application
SET academic_year_id = resolved.academic_year_id
FROM resolved
WHERE application.enrollment_application_id = resolved.enrollment_application_id
  AND application.academic_year_id IS NULL;

DROP INDEX enrollment_apps_applicant_open_path_unique;
CREATE UNIQUE INDEX enrollment_apps_applicant_open_path_year_unique
    ON enrollment_applications(institution_id, applicant_person_id, training_path_id, academic_year_id)
    WHERE deleted_at IS NULL AND status = 'DRAFT'
      AND enrollment_period_id IS NULL AND academic_year_id IS NOT NULL;

-- Require a fixed cycle for new applications without inventing one for old empty drafts.
CREATE FUNCTION validate_enrollment_application_academic_year() RETURNS trigger LANGUAGE plpgsql AS $$
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

CREATE TRIGGER enrollment_application_academic_year
    BEFORE INSERT OR UPDATE OF academic_year_id, institution_id, enrollment_period_id
    ON enrollment_applications
    FOR EACH ROW EXECUTE FUNCTION validate_enrollment_application_academic_year();
