-- Extend the opt-in Envers history with explicit, non-sensitive field selections.
-- Additive only: no business data changes, history backfill or references to live rows.

CREATE TABLE envers_poc_academic_levels_aud (
    display_order integer,
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    academic_level_id uuid not null,
    study_plan_id uuid,
    name varchar(150),
    PRIMARY KEY (academic_level_id, rev)
);

CREATE INDEX evx_academic_levels_parent_rev_idx ON envers_poc_academic_levels_aud(study_plan_id, rev);

CREATE TABLE envers_poc_academic_spaces_aud (
    active boolean,
    instrumental boolean,
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    deleted_at timestamp(6) with time zone,
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    academic_space_id uuid not null,
    institution_id uuid,
    format varchar(20),
    type varchar(20),
    name varchar(150),
    PRIMARY KEY (academic_space_id, rev)
);

CREATE INDEX evx_academic_spaces_tenant_rev_idx ON envers_poc_academic_spaces_aud(institution_id, rev);

CREATE TABLE envers_poc_academic_years_aud (
    end_date date,
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    start_date date,
    "year" integer,
    deleted_at timestamp(6) with time zone,
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    academic_year_id uuid not null,
    institution_id uuid,
    status varchar(20),
    PRIMARY KEY (academic_year_id, rev)
);

CREATE INDEX evx_academic_years_tenant_rev_idx ON envers_poc_academic_years_aud(institution_id, rev);

CREATE TABLE envers_poc_applicant_preferences_aud (
    allows_image_use boolean,
    is_reenrolling boolean,
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    deleted_at timestamp(6) with time zone,
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    applicant_preference_id uuid not null,
    enrollment_application_id uuid,
    PRIMARY KEY (applicant_preference_id, rev)
);

CREATE INDEX evx_applicant_preferences_parent_rev_idx ON envers_poc_applicant_preferences_aud(enrollment_application_id, rev);

CREATE TABLE envers_poc_course_enrollment_schedules_aud (
    end_time time(6),
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    start_time time(6),
    released_at timestamp(6) with time zone,
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    course_class_schedule_id uuid,
    course_enrollment_id uuid,
    course_enrollment_schedule_id uuid not null,
    course_individual_slot_id uuid,
    institution_id uuid,
    day_of_week varchar(20),
    PRIMARY KEY (course_enrollment_schedule_id, rev)
);

CREATE INDEX evx_course_enrollment_schedules_tenant_rev_idx ON envers_poc_course_enrollment_schedules_aud(institution_id, rev);
CREATE INDEX evx_course_enrollment_schedules_parent_rev_idx ON envers_poc_course_enrollment_schedules_aud(course_enrollment_id, rev);

CREATE TABLE envers_poc_course_enrollments_aud (
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    completed_at timestamp(6) with time zone,
    enrolled_at timestamp(6) with time zone,
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    withdrawn_at timestamp(6) with time zone,
    action_authority_person_id uuid,
    course_class_id uuid,
    course_enrollment_id uuid not null,
    course_id uuid,
    enrollment_application_course_id uuid,
    enrollment_application_id uuid,
    institution_id uuid,
    student_id uuid,
    source varchar(20),
    academic_status varchar(30),
    status varchar(40),
    PRIMARY KEY (course_enrollment_id, rev)
);

CREATE INDEX evx_course_enrollments_tenant_rev_idx ON envers_poc_course_enrollments_aud(institution_id, rev);
CREATE INDEX evx_course_enrollments_parent_rev_idx ON envers_poc_course_enrollments_aud(student_id, rev);

CREATE TABLE envers_poc_document_definitions_aud (
    active boolean,
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    id uuid not null,
    institution_id uuid,
    name varchar(150),
    allowed_formats jsonb,
    PRIMARY KEY (id, rev)
);

CREATE INDEX evx_document_definitions_tenant_rev_idx ON envers_poc_document_definitions_aud(institution_id, rev);

CREATE TABLE envers_poc_enrollment_application_courses_aud (
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    submitted_with_capacity boolean,
    waitlist_number integer,
    requested_at timestamp(6) with time zone,
    resolved_at timestamp(6) with time zone,
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    waitlisted_at timestamp(6) with time zone,
    course_id uuid,
    enrollment_application_course_id uuid not null,
    enrollment_application_id uuid,
    enrollment_period_id uuid,
    institution_id uuid,
    preferred_teacher_id uuid,
    resolved_by_person_id uuid,
    status varchar(20),
    resolution_reason_code varchar(80),
    waitlist_reason varchar(80),
    PRIMARY KEY (enrollment_application_course_id, rev)
);

CREATE INDEX evx_enrollment_application_courses_tenant_rev_idx ON envers_poc_enrollment_application_courses_aud(institution_id, rev);
CREATE INDEX evx_enrollment_application_courses_parent_rev_idx ON envers_poc_enrollment_application_courses_aud(enrollment_application_id, rev);

CREATE TABLE envers_poc_enrollment_application_spaces_aud (
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    enrollment_application_id uuid,
    enrollment_application_space_id uuid not null,
    instrument_id uuid,
    study_plan_space_id uuid,
    PRIMARY KEY (enrollment_application_space_id, rev)
);

CREATE INDEX evx_enrollment_application_spaces_parent_rev_idx ON envers_poc_enrollment_application_spaces_aud(enrollment_application_id, rev);

CREATE TABLE envers_poc_enrollment_applications_aud (
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    deleted_at timestamp(6) with time zone,
    resolved_at timestamp(6) with time zone,
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    academic_year_id uuid,
    applicant_person_id uuid,
    enrollment_application_id uuid not null,
    enrollment_period_id uuid,
    institution_id uuid,
    resolved_by_person_id uuid,
    study_plan_id uuid,
    submitted_by_person_id uuid,
    training_path_id uuid,
    status varchar(40),
    PRIMARY KEY (enrollment_application_id, rev)
);

CREATE INDEX evx_enrollment_applications_tenant_rev_idx ON envers_poc_enrollment_applications_aud(institution_id, rev);

CREATE TABLE envers_poc_enrollment_attachments_aud (
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    deleted_at timestamp(6) with time zone,
    file_size bigint,
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    reviewed_at timestamp(6) with time zone,
    enrollment_application_id uuid,
    enrollment_attachment_id uuid not null,
    requirement_id uuid,
    reviewed_by uuid,
    uploaded_by uuid,
    content_type varchar(100),
    review_status varchar(255),
    reviewer_type varchar(255),
    uploader_type varchar(255),
    version_status varchar(255),
    PRIMARY KEY (enrollment_attachment_id, rev)
);

CREATE INDEX evx_enrollment_attachments_parent_rev_idx ON envers_poc_enrollment_attachments_aud(enrollment_application_id, rev);

CREATE TABLE envers_poc_enrollment_document_requests_aud (
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    created_at timestamp(6) with time zone,
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    actor_id uuid,
    application_id uuid,
    id uuid not null,
    institution_id uuid,
    account_type varchar(255),
    PRIMARY KEY (id, rev)
);

CREATE INDEX evx_enrollment_document_requests_tenant_rev_idx ON envers_poc_enrollment_document_requests_aud(institution_id, rev);
CREATE INDEX evx_enrollment_document_requests_parent_rev_idx ON envers_poc_enrollment_document_requests_aud(application_id, rev);

CREATE TABLE envers_poc_enrollment_document_requirements_aud (
    active boolean,
    display_order integer,
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    assignment_revision bigint,
    definition_revision bigint,
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    application_id uuid,
    document_id uuid,
    id uuid not null,
    institution_id uuid,
    request_id uuid,
    source_requirement_id uuid,
    name varchar(150),
    level varchar(255),
    origin varchar(255),
    allowed_formats jsonb,
    PRIMARY KEY (id, rev)
);

CREATE INDEX evx_enrollment_document_requirements_tenant_rev_idx ON envers_poc_enrollment_document_requirements_aud(institution_id, rev);
CREATE INDEX evx_enrollment_document_requirements_parent_rev_idx ON envers_poc_enrollment_document_requirements_aud(application_id, rev);

CREATE TABLE envers_poc_enrollment_period_offering_levels_aud (
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    academic_level_id uuid,
    offering_id uuid,
    offering_level_id uuid not null,
    PRIMARY KEY (offering_level_id, rev)
);

CREATE INDEX evx_enrollment_period_offering_levels_parent_rev_idx ON envers_poc_enrollment_period_offering_levels_aud(offering_id, rev);

CREATE TABLE envers_poc_enrollment_period_offerings_aud (
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    enrollment_period_id uuid,
    offering_id uuid not null,
    study_plan_id uuid,
    PRIMARY KEY (offering_id, rev)
);

CREATE INDEX evx_enrollment_period_offerings_parent_rev_idx ON envers_poc_enrollment_period_offerings_aud(enrollment_period_id, rev);

CREATE TABLE envers_poc_enrollment_periods_aud (
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    scope_configured boolean,
    deleted_at timestamp(6) with time zone,
    end_date timestamp(6) with time zone,
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    start_date timestamp(6) with time zone,
    academic_year_id uuid,
    enrollment_period_id uuid not null,
    institution_id uuid,
    status varchar(20),
    name varchar(150),
    PRIMARY KEY (enrollment_period_id, rev)
);

CREATE INDEX evx_enrollment_periods_tenant_rev_idx ON envers_poc_enrollment_periods_aud(institution_id, rev);

CREATE TABLE envers_poc_institutions_aud (
    active boolean,
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    logo_size bigint,
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    city_id uuid,
    institution_id uuid not null,
    logo_content_type varchar(32),
    logo_version varchar(36),
    public_subdomain varchar(63),
    slug varchar(100),
    name varchar(255),
    PRIMARY KEY (institution_id, rev)
);

CREATE INDEX evx_institutions_tenant_rev_idx ON envers_poc_institutions_aud(institution_id, rev);

CREATE TABLE envers_poc_instruments_aud (
    active boolean,
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    deleted_at timestamp(6) with time zone,
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    institution_id uuid,
    instrument_id uuid not null,
    name varchar(150),
    PRIMARY KEY (instrument_id, rev)
);

CREATE INDEX evx_instruments_tenant_rev_idx ON envers_poc_instruments_aud(institution_id, rev);

CREATE TABLE envers_poc_passkey_credentials_aud (
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    revoked_at timestamp(6) with time zone,
    passkey_credential_id uuid not null,
    user_id uuid,
    PRIMARY KEY (passkey_credential_id, rev)
);

CREATE INDEX evx_passkey_credentials_parent_rev_idx ON envers_poc_passkey_credentials_aud(user_id, rev);

CREATE TABLE envers_poc_people_aud (
    deleted boolean,
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    institution_id uuid,
    person_id uuid not null,
    PRIMARY KEY (person_id, rev)
);

CREATE INDEX evx_people_tenant_rev_idx ON envers_poc_people_aud(institution_id, rev);

CREATE TABLE envers_poc_person_access_invitations_aud (
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    consumed_at timestamp(6) with time zone,
    expires_at timestamp(6) with time zone,
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    institutional_issuer_person_id uuid,
    invitation_id uuid not null,
    issuing_institution_id uuid,
    person_id uuid,
    representation_id uuid,
    PRIMARY KEY (invitation_id, rev)
);

CREATE INDEX evx_person_access_invitations_parent_rev_idx ON envers_poc_person_access_invitations_aud(person_id, rev);

CREATE TABLE envers_poc_person_guardian_attachments_aud (
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    file_size bigint,
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    institution_id uuid,
    person_guardian_attachment_id uuid not null,
    person_guardian_id uuid,
    content_type varchar(100),
    PRIMARY KEY (person_guardian_attachment_id, rev)
);

CREATE INDEX evx_person_guardian_attachments_tenant_rev_idx ON envers_poc_person_guardian_attachments_aud(institution_id, rev);
CREATE INDEX evx_person_guardian_attachments_parent_rev_idx ON envers_poc_person_guardian_attachments_aud(person_guardian_id, rev);

CREATE TABLE envers_poc_person_guardians_aud (
    is_primary_contact boolean,
    own_access_requested boolean,
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    resolved_at timestamp(6) with time zone,
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    dependent_person_id uuid,
    institution_id uuid,
    person_guardian_id uuid not null,
    resolved_by_person_id uuid,
    tutor_person_id uuid,
    status varchar(20),
    relationship varchar(30),
    PRIMARY KEY (person_guardian_id, rev)
);

CREATE INDEX evx_person_guardians_tenant_rev_idx ON envers_poc_person_guardians_aud(institution_id, rev);

CREATE TABLE envers_poc_person_role_assignment_training_paths_aud (
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    person_role_assignment_id uuid not null,
    training_path_id uuid not null,
    PRIMARY KEY (person_role_assignment_id, training_path_id, rev)
);

CREATE INDEX evx_person_role_assignment_training_paths_parent_rev_idx ON envers_poc_person_role_assignment_training_paths_aud(person_role_assignment_id, rev);

CREATE TABLE envers_poc_person_role_assignments_aud (
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    institution_id uuid,
    person_id uuid,
    person_role_assignment_id uuid not null,
    role_id uuid,
    access_scope varchar(255),
    PRIMARY KEY (person_role_assignment_id, rev)
);

CREATE INDEX evx_person_role_assignments_tenant_rev_idx ON envers_poc_person_role_assignments_aud(institution_id, rev);
CREATE INDEX evx_person_role_assignments_parent_rev_idx ON envers_poc_person_role_assignments_aud(person_id, rev);

CREATE TABLE envers_poc_platform_account_roles_aud (
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    platform_account_id uuid,
    platform_account_role_id uuid not null,
    role_id uuid,
    PRIMARY KEY (platform_account_role_id, rev)
);

CREATE INDEX evx_platform_account_roles_parent_rev_idx ON envers_poc_platform_account_roles_aud(platform_account_id, rev);

CREATE TABLE envers_poc_platform_accounts_aud (
    enabled boolean,
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    platform_account_id uuid not null,
    PRIMARY KEY (platform_account_id, rev)
);


CREATE TABLE envers_poc_prerequisites_aud (
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    prerequisite_id uuid not null,
    required_study_plan_space_id uuid,
    study_plan_id uuid,
    target_study_plan_space_id uuid,
    required_condition varchar(20),
    requirement_stage varchar(20),
    PRIMARY KEY (prerequisite_id, rev)
);

CREATE INDEX evx_prerequisites_parent_rev_idx ON envers_poc_prerequisites_aud(study_plan_id, rev);

CREATE TABLE envers_poc_role_permissions_aud (
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    permission_id uuid not null,
    role_id uuid not null,
    PRIMARY KEY (permission_id, role_id, rev)
);

CREATE INDEX evx_role_permissions_parent_rev_idx ON envers_poc_role_permissions_aud(role_id, rev);

CREATE TABLE envers_poc_roles_aud (
    is_system boolean,
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    institution_id uuid,
    role_id uuid not null,
    scope varchar(20),
    code varchar(50),
    name varchar(100),
    PRIMARY KEY (role_id, rev)
);

CREATE INDEX evx_roles_tenant_rev_idx ON envers_poc_roles_aud(institution_id, rev);

CREATE TABLE envers_poc_students_aud (
    enrollment_date date,
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    institution_id uuid,
    person_id uuid,
    student_id uuid not null,
    status varchar(30),
    PRIMARY KEY (student_id, rev)
);

CREATE INDEX evx_students_tenant_rev_idx ON envers_poc_students_aud(institution_id, rev);

CREATE TABLE envers_poc_study_plan_space_instruments_aud (
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    institution_id uuid,
    instrument_id uuid,
    study_plan_space_id uuid,
    study_plan_space_instrument_id uuid not null,
    PRIMARY KEY (study_plan_space_instrument_id, rev)
);

CREATE INDEX evx_study_plan_space_instruments_tenant_rev_idx ON envers_poc_study_plan_space_instruments_aud(institution_id, rev);
CREATE INDEX evx_study_plan_space_instruments_parent_rev_idx ON envers_poc_study_plan_space_instruments_aud(study_plan_space_id, rev);

CREATE TABLE envers_poc_study_plan_spaces_aud (
    display_order integer,
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    academic_level_id uuid,
    academic_space_id uuid,
    institution_id uuid,
    study_plan_id uuid,
    study_plan_space_id uuid not null,
    requirement_type varchar(20),
    approval_mode varchar(30),
    PRIMARY KEY (study_plan_space_id, rev)
);

CREATE INDEX evx_study_plan_spaces_tenant_rev_idx ON envers_poc_study_plan_spaces_aud(institution_id, rev);

CREATE TABLE envers_poc_study_plans_aud (
    effective_from date,
    effective_to date,
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    version_number integer,
    deleted_at timestamp(6) with time zone,
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    institution_id uuid,
    previous_version_id uuid,
    study_plan_id uuid not null,
    training_path_id uuid,
    status varchar(20),
    name varchar(150),
    PRIMARY KEY (study_plan_id, rev)
);

CREATE INDEX evx_study_plans_tenant_rev_idx ON envers_poc_study_plans_aud(institution_id, rev);

CREATE TABLE envers_poc_training_path_document_requirements_aud (
    active boolean,
    display_order integer,
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    document_id uuid,
    id uuid not null,
    institution_id uuid,
    training_path_id uuid,
    level varchar(255),
    PRIMARY KEY (id, rev)
);

CREATE INDEX evx_training_path_document_requirements_tenant_rev_idx ON envers_poc_training_path_document_requirements_aud(institution_id, rev);
CREATE INDEX evx_training_path_document_requirements_parent_rev_idx ON envers_poc_training_path_document_requirements_aud(training_path_id, rev);

CREATE TABLE envers_poc_training_paths_aud (
    active boolean,
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    deleted_at timestamp(6) with time zone,
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    institution_id uuid,
    training_path_id uuid not null,
    name varchar(150),
    PRIMARY KEY (training_path_id, rev)
);

CREATE INDEX evx_training_paths_tenant_rev_idx ON envers_poc_training_paths_aud(institution_id, rev);

CREATE TABLE envers_poc_users_aud (
    enabled boolean,
    revtype SMALLINT NOT NULL CHECK (revtype IN (0, 1, 2)),
    self_access_enabled boolean,
    email_verified_at timestamp(6) with time zone,
    rev BIGINT NOT NULL REFERENCES envers_poc_revisions(id),
    institution_id uuid,
    person_id uuid,
    user_id uuid not null,
    email_verification_status varchar(24),
    PRIMARY KEY (user_id, rev)
);

CREATE INDEX evx_users_tenant_rev_idx ON envers_poc_users_aud(institution_id, rev);
