-- Dependents registered before tutors granted them the applicant role have no role at all,
-- so role-based listings miss them. Grant it to every linked dependent that has none, which
-- mirrors what registering a dependent does now. People that already hold a role (for example
-- a student) are left alone, because the applicant role must not replace other roles.
INSERT INTO person_role_assignments (
    person_role_assignment_id, person_id, role_id, institution_id, created_at, updated_at
)
SELECT gen_random_uuid(), dependents.dependent_person_id, role.role_id, dependents.institution_id,
       CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC'
FROM (
    SELECT DISTINCT institution_id, dependent_person_id
    FROM person_guardians
) dependents
JOIN roles role
    ON role.institution_id = dependents.institution_id
    AND role.scope = 'INSTITUTION'
    AND role.code = 'APPLICANT'
WHERE NOT EXISTS (
    SELECT 1
    FROM person_role_assignments assignment
    WHERE assignment.person_id = dependents.dependent_person_id
      AND assignment.institution_id = dependents.institution_id
);
