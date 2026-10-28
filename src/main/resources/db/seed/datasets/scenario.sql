-- Fictional operational scenario, independent of the connection and target institution.
CREATE TEMP TABLE seed_settings (
    password_hash text NOT NULL, email_domain text NOT NULL,
    plan_edition text NOT NULL, plan_effective_from date NOT NULL,
    offered_level integer NOT NULL CHECK (offered_level > 0),
    weekdays text[] NOT NULL CHECK (cardinality(weekdays) > 0),
    day_start time NOT NULL, block_minutes integer NOT NULL CHECK (block_minutes > 0),
    blocks_per_day integer NOT NULL CHECK (blocks_per_day > 0),
    group_capacity integer NOT NULL CHECK (group_capacity > 0),
    group_teachers integer[] NOT NULL CHECK (cardinality(group_teachers) > 0)
) ON COMMIT DROP;
-- Public password for new demo users: BemolDemo2026! Existing passwords are never reset on reruns.
INSERT INTO seed_settings VALUES (
    '$2b$12$DZAvKR0GDDcAMSfi37vTzOUh.Z0dqsXkZm5MollLzfZnKN9yVZnSi', 'example.test',
    '2025', DATE '2025-01-01', 1,
    ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'], TIME '14:00', 60, 7, 20, ARRAY[5, 6]
);

CREATE TEMP TABLE seed_periods (
    code text PRIMARY KEY, name text NOT NULL,
    start_date date NOT NULL, end_date date NOT NULL,
    CHECK (start_date <= end_date)
) ON COMMIT DROP;
INSERT INTO seed_periods
SELECT 'vocacionales', 'Ciclos Artísticos Vocacionales', make_date(academic_year, 8, 1), make_date(academic_year, 11, 30)
FROM seed_context
UNION ALL
SELECT 'profesorado', 'Profesorado de Música', make_date(academic_year, 9, 1), make_date(academic_year, 12, 15)
FROM seed_context;

CREATE TEMP TABLE seed_program_periods (
    program text PRIMARY KEY REFERENCES seed_programs,
    period_code text NOT NULL REFERENCES seed_periods
) ON COMMIT DROP;
INSERT INTO seed_program_periods VALUES
    ('cavi', 'vocacionales'),
    ('cavb-instrumento', 'vocacionales'),
    ('cavb-canto', 'vocacionales'),
    ('cava-instrumento', 'vocacionales'),
    ('cava-canto', 'vocacionales'),
    ('isfd', 'profesorado');

CREATE TEMP TABLE seed_members (
    number integer PRIMARY KEY, document text NOT NULL UNIQUE,
    first_name text NOT NULL, last_name text NOT NULL,
    age integer NOT NULL CHECK (age >= 0), role_code text NOT NULL,
    student_file_suffix text UNIQUE,
    CHECK ((role_code = 'STUDENT') = (student_file_suffix IS NOT NULL))
) ON COMMIT DROP;
-- Ages are relative to SEED_REFERENCE_DATE, not tied to a calendar year.
INSERT INTO seed_members VALUES
    (1, '99000001', 'Lucía', 'Ferreyra', 8, 'APPLICANT', NULL),
    (2, '99000002', 'Mateo', 'Soria', 37, 'APPLICANT', NULL),
    (3, '99000003', 'Camila', 'Roldán', 23, 'STUDENT', '0001'),
    (4, '99000004', 'Julián', 'Pereyra', 25, 'STUDENT', '0002'),
    (5, '99000005', 'Valeria', 'Molina', 40, 'TEACHER', NULL),
    (6, '99000006', 'Gabriel', 'Acosta', 47, 'TEACHER', NULL),
    (7, '99000007', 'Mariana', 'Suárez', 44, 'REPRESENTATIVE', NULL),
    (8, '99000008', 'Nicolás', 'Herrera', 36, 'INSTITUTIONAL_AUTHORITY', NULL),
    (21, '99000021', 'Sofía', 'Ferreyra', 9, 'APPLICANT', NULL),
    (22, '99000022', 'Bruno', 'Ferreyra', 10, 'APPLICANT', NULL),
    (23, '99000023', 'Elena', 'Vega', 31, 'STUDENT', '0003'),
    (24, '99000024', 'Tomás', 'Benítez', 29, 'APPLICANT', NULL),
    (25, '99000025', 'Paula', 'Castro', 30, 'APPLICANT', NULL),
    (26, '99000026', 'Santiago', 'Luna', 28, 'APPLICANT', NULL),
    (27, '99000027', 'Laura', 'Díaz', 35, 'DEMO_ADMINISTRATIVE', NULL),
    (28, '99000028', 'Florencia', 'Ruiz', 32, 'APPLICANT', NULL),
    (29, '99000029', 'Martín', 'Torres', 34, 'APPLICANT', NULL);

CREATE TEMP TABLE seed_custom_roles (code text PRIMARY KEY, name text NOT NULL, template_code text NOT NULL) ON COMMIT DROP;
INSERT INTO seed_custom_roles VALUES ('DEMO_ADMINISTRATIVE', 'Administración CAVB (demo)', 'ADMINISTRATIVE');

CREATE TEMP TABLE seed_instruments (
    name text PRIMARY KEY, offered boolean NOT NULL,
    teacher_number integer REFERENCES seed_members,
    CHECK (NOT offered OR teacher_number IS NOT NULL)
) ON COMMIT DROP;
INSERT INTO seed_instruments VALUES
    ('Guitarra', true, 6), ('Piano', true, 5), ('Violín', false, NULL),
    ('Violoncello', false, NULL), ('Saxofón', false, NULL), ('Clarinete', false, NULL),
    ('Flauta traversa', false, NULL), ('Trompeta', false, NULL), ('Percusión', false, NULL);

CREATE TEMP TABLE seed_instrument_groups (
    group_code text, instrument text REFERENCES seed_instruments,
    PRIMARY KEY (group_code, instrument)
) ON COMMIT DROP;
INSERT INTO seed_instrument_groups SELECT 'cav', name FROM seed_instruments;
INSERT INTO seed_instrument_groups VALUES ('harmonic', 'Piano'), ('harmonic', 'Guitarra');

CREATE TEMP TABLE seed_shifts (name text PRIMARY KEY) ON COMMIT DROP;
INSERT INTO seed_shifts VALUES ('Mañana'), ('Tarde'), ('Vespertino');

CREATE TEMP TABLE seed_guardians (
    member_number integer PRIMARY KEY REFERENCES seed_members,
    tutor_number integer NOT NULL REFERENCES seed_members,
    reviewer_number integer NOT NULL REFERENCES seed_members,
    status text NOT NULL CHECK (status IN ('ACTIVE', 'PENDING'))
) ON COMMIT DROP;
INSERT INTO seed_guardians VALUES (1, 7, 8, 'ACTIVE'), (3, 7, 8, 'ACTIVE'),
    (21, 7, 8, 'ACTIVE'), (22, 7, 8, 'PENDING');

CREATE TEMP TABLE seed_documents (
    code text PRIMARY KEY, name text NOT NULL, instructions text NOT NULL,
    level text NOT NULL CHECK (level IN ('AT_SUBMISSION', 'BEFORE_CONFIRMATION', 'OPTIONAL')),
    position integer NOT NULL, asset text NOT NULL
) ON COMMIT DROP;
INSERT INTO seed_documents VALUES
    ('dni', 'Documento Nacional de Identidad (DNI)', 'Adjuntá una copia legible del frente y dorso de tu DNI.',
     'AT_SUBMISSION', 10, 'dni-muestra.pdf'),
    ('aptitud-fisica', 'Certificado de aptitud física', 'Presentá un certificado de aptitud física vigente para completar la inscripción.',
     'BEFORE_CONFIRMATION', 20, 'certificado-aptitud-fisica-muestra.pdf'),
    ('musical-presentation', 'Presentación musical personal', 'Contanos qué te interesa aprender y tu experiencia con la música.',
     'OPTIONAL', 100, 'presentacion-musical.pdf');

CREATE TEMP TABLE seed_applications (
    scenario text PRIMARY KEY, member_number integer NOT NULL UNIQUE REFERENCES seed_members,
    actor_number integer NOT NULL REFERENCES seed_members, program text NOT NULL REFERENCES seed_programs,
    status text NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'PROVISIONALLY_APPROVED', 'APPROVED', 'REJECTED', 'CANCELLED')),
    documents text NOT NULL CHECK (documents IN ('MISSING', 'ACCEPTED', 'INITIAL_PENDING', 'DEFERRED_MISSING', 'OBSERVED', 'REPLACED')),
    courses text NOT NULL CHECK (courses IN ('PENDING', 'ENROLLED', 'WAITLISTED', 'REJECTED', 'CANCELLED')),
    positions integer[] NOT NULL, instrument text NOT NULL, completed_position integer
) ON COMMIT DROP;
INSERT INTO seed_applications VALUES
    ('dependent:1', 1, 7, 'cavi', 'SUBMITTED', 'INITIAL_PENDING', 'PENDING', ARRAY[1,2,3], 'Piano', NULL),
    ('dependent:3', 3, 7, 'cavb-instrumento', 'APPROVED', 'ACCEPTED', 'ENROLLED', ARRAY[1,2,3], 'Piano', NULL),
    ('dependent:21', 21, 7, 'cavi', 'DRAFT', 'MISSING', 'PENDING', ARRAY[1,2,3], 'Guitarra', NULL),
    ('self:4', 4, 4, 'cavb-instrumento', 'APPROVED', 'ACCEPTED', 'ENROLLED', ARRAY[1,3], 'Piano', 1),
    ('self:23', 23, 23, 'cavb-canto', 'PROVISIONALLY_APPROVED', 'DEFERRED_MISSING', 'ENROLLED', ARRAY[1,2,3], 'Piano', NULL),
    ('self:24', 24, 24, 'cava-canto', 'SUBMITTED', 'OBSERVED', 'PENDING', ARRAY[1,2,3], 'Piano', NULL),
    ('self:25', 25, 25, 'cava-instrumento', 'SUBMITTED', 'REPLACED', 'PENDING', ARRAY[1,2,3], 'Piano', NULL),
    ('self:26', 26, 26, 'cavb-instrumento', 'APPROVED', 'ACCEPTED', 'WAITLISTED', ARRAY[3], 'Piano', NULL),
    ('self:28', 28, 28, 'cava-canto', 'REJECTED', 'ACCEPTED', 'REJECTED', ARRAY[1], 'Piano', NULL),
    ('self:29', 29, 29, 'cava-canto', 'CANCELLED', 'MISSING', 'CANCELLED', ARRAY[1], 'Piano', NULL);

CREATE TEMP TABLE seed_role_scopes (
    member_number integer REFERENCES seed_members, program text REFERENCES seed_programs,
    PRIMARY KEY (member_number, program)
) ON COMMIT DROP;
INSERT INTO seed_role_scopes VALUES (27, 'cavb-instrumento');
