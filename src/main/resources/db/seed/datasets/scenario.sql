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
-- Public demo password: BoeroDemo2026! Kept for compatibility, never reset on reruns.
INSERT INTO seed_settings VALUES (
    '$2b$12$xwT.Abo0Q4vFReOL.rFEvODBYAegel/G1HE96wmS1S4Rm7VVpAyla', 'example.test',
    '2025', DATE '2025-01-01', 1,
    ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'], TIME '14:00', 60, 7, 20, ARRAY[5, 6]
);

CREATE TEMP TABLE seed_members (
    number integer PRIMARY KEY, document text NOT NULL UNIQUE,
    first_name text NOT NULL, last_name text NOT NULL,
    age integer NOT NULL CHECK (age >= 0), role_code text NOT NULL,
    student_file_suffix text UNIQUE,
    CHECK ((role_code = 'STUDENT') = (student_file_suffix IS NOT NULL))
) ON COMMIT DROP;
-- Ages are relative to SEED_REFERENCE_DATE, not tied to a calendar year.
INSERT INTO seed_members VALUES
    (1, '99000001', 'Lucía', 'Ferreyra', 4, 'APPLICANT', NULL),
    (2, '99000002', 'Mateo', 'Soria', 37, 'APPLICANT', NULL),
    (3, '99000003', 'Camila', 'Roldán', 23, 'STUDENT', '0001'),
    (4, '99000004', 'Julián', 'Pereyra', 25, 'STUDENT', '0002'),
    (5, '99000005', 'Valeria', 'Molina', 40, 'TEACHER', NULL),
    (6, '99000006', 'Gabriel', 'Acosta', 47, 'TEACHER', NULL),
    (7, '99000007', 'Mariana', 'Suárez', 44, 'INSTITUTIONAL_AUTHORITY', NULL),
    (8, '99000008', 'Nicolás', 'Herrera', 36, 'INSTITUTIONAL_AUTHORITY', NULL);

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
