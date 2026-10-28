-- Only new demo people receive these roles and links; reruns preserve subsequent changes.
DO $$
DECLARE
    tenant uuid := (SELECT institution_id FROM seed_context);
    cycle_year integer := (SELECT academic_year FROM seed_context);
    settings record;
    fixture record;
    created_person uuid;
    role_code text;
    role_key uuid;
    dependent uuid := pg_temp.demo_id('person:3');
BEGIN
    SELECT * INTO STRICT settings FROM seed_settings;

    FOR fixture IN SELECT * FROM (VALUES
        (9, '99000009', 'Ana', 'Vega', 42, ARRAY['REPRESENTATIVE']),
        (10, '99000010', 'Sofia', 'Rios', 27, ARRAY['APPLICANT', 'REPRESENTATIVE']),
        (11, '99000011', 'Diego', 'Luna', 31, ARRAY['STUDENT', 'REPRESENTATIVE'])
    ) AS scenarios(number, document, first_name, last_name, age, role_codes)
    LOOP
        INSERT INTO people (person_id, institution_id, document_number, first_name, last_name,
                            email, birth_date, deleted, created_at, updated_at)
        VALUES (pg_temp.demo_id('person:' || fixture.number), tenant, fixture.document,
                fixture.first_name, fixture.last_name,
                lower(fixture.first_name || '.' || fixture.last_name) || '@' || settings.email_domain,
                ((SELECT reference_date FROM seed_context) - make_interval(years => fixture.age))::date,
                false, now(), now())
        ON CONFLICT (person_id) DO NOTHING RETURNING person_id INTO created_person;

        IF created_person IS NULL THEN
            CONTINUE;
        END IF;

        INSERT INTO users (user_id, institution_id, person_id, password, enabled, self_access_enabled,
                           email_verification_status, email_verified_at, created_at, updated_at)
        VALUES (pg_temp.demo_id('user:' || fixture.number), tenant, created_person,
                settings.password_hash, true, true, 'VERIFIED', now(), now(), now());

        FOREACH role_code IN ARRAY fixture.role_codes LOOP
            SELECT role_id INTO STRICT role_key FROM roles
            WHERE institution_id = tenant AND code = role_code AND is_system;

            INSERT INTO person_role_assignments (person_role_assignment_id, institution_id,
                                                person_id, role_id, access_scope, created_at, updated_at)
            VALUES (pg_temp.demo_id('representative-role:' || fixture.number || ':' || role_code),
                    tenant, created_person, role_key, 'INSTITUTION', now(), now());
        END LOOP;

        IF 'STUDENT' = ANY(fixture.role_codes) THEN
            INSERT INTO students (student_id, institution_id, person_id, enrollment_date,
                                  status, file_number, created_at, updated_at)
            VALUES (pg_temp.demo_id('student:' || fixture.number), tenant, created_person,
                    (SELECT reference_date FROM seed_context), 'ACTIVE',
                    cycle_year || '-00' || fixture.number, now(), now());
        END IF;

        INSERT INTO person_guardians (person_guardian_id, institution_id, tutor_person_id,
                                     dependent_person_id, declared_document_number, request_mode,
                                     relationship, is_primary_contact, status, resolved_by_person_id,
                                     resolved_at, created_at, updated_at)
        VALUES (pg_temp.demo_id('guardian:' || fixture.number || ':3'), tenant, created_person,
                dependent, '99000003', 'EXISTING', 'OTHER', false, 'ACTIVE',
                pg_temp.demo_id('person:8'), now(), now(), now());
    END LOOP;
END $$;
