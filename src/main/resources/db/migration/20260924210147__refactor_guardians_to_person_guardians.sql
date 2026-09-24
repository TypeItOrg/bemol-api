-- Guardianship moves from student-bound profiles to a direct person-to-person link,
-- so a tutor can act for a minor before that minor is formally enrolled as a student.

-- Minors do not have their own email.
ALTER TABLE people
    ALTER COLUMN email DROP NOT NULL;

ALTER TABLE people
    DROP CONSTRAINT people_email_not_blank;

ALTER TABLE people
    ADD CONSTRAINT people_email_not_blank CHECK (email IS NULL OR btrim(email) <> '');

CREATE TABLE person_guardians (
    person_guardian_id  UUID NOT NULL,
    institution_id      UUID NOT NULL,
    tutor_person_id     UUID NOT NULL,
    dependent_person_id UUID NOT NULL,
    relationship        VARCHAR(30) NOT NULL,
    is_primary_contact  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL,

    CONSTRAINT person_guardians_pkey PRIMARY KEY (person_guardian_id),
    CONSTRAINT person_guardians_unique
        UNIQUE (institution_id, tutor_person_id, dependent_person_id),
    CONSTRAINT person_guardians_no_self_guardianship
        CHECK (tutor_person_id <> dependent_person_id),
    CONSTRAINT person_guardians_relationship_check
        CHECK (relationship IN ('MOTHER', 'FATHER', 'LEGAL_GUARDIAN', 'OTHER')),
    CONSTRAINT person_guardians_tutor_institution_fk
        FOREIGN KEY (institution_id, tutor_person_id)
        REFERENCES people (institution_id, person_id),
    CONSTRAINT person_guardians_dependent_institution_fk
        FOREIGN KEY (institution_id, dependent_person_id)
        REFERENCES people (institution_id, person_id)
);

-- "Who are the dependents of this tutor" is the hot path; the unique index already leads
-- with (institution_id, tutor_person_id). This one serves "who are the tutors of this person".
CREATE INDEX person_guardians_dependent_idx
    ON person_guardians (institution_id, dependent_person_id);

-- Carry existing links over before the old tables go away. Guardian profile extras
-- (education level, occupation) have no place in the new model and are intentionally dropped.
INSERT INTO person_guardians (
    person_guardian_id, institution_id, tutor_person_id, dependent_person_id,
    relationship, is_primary_contact, created_at, updated_at
)
SELECT gen_random_uuid(), sg.institution_id, gp.person_id, s.person_id,
       sg.relationship, FALSE, sg.created_at AT TIME ZONE 'UTC', sg.updated_at AT TIME ZONE 'UTC'
FROM student_guardians sg
JOIN guardian_profiles gp
    ON gp.institution_id = sg.institution_id AND gp.guardian_profile_id = sg.guardian_profile_id
JOIN students s
    ON s.institution_id = sg.institution_id AND s.student_id = sg.student_id
WHERE gp.person_id <> s.person_id;

DROP TABLE student_guardians;
DROP TABLE guardian_profiles;

-- Who physically submitted the application (the tutor when acting for a minor).
ALTER TABLE enrollment_applications
    ADD COLUMN submitted_by_person_id UUID;

ALTER TABLE enrollment_applications
    ADD CONSTRAINT enrollment_applications_submitted_by_institution_fk
    FOREIGN KEY (institution_id, submitted_by_person_id)
    REFERENCES people (institution_id, person_id);

CREATE INDEX enrollment_applications_submitted_by_idx
    ON enrollment_applications (institution_id, submitted_by_person_id);
