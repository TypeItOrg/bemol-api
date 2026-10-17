ALTER TABLE users ADD COLUMN self_access_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE person_guardians ADD COLUMN own_access_requested BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE person_guardians ADD COLUMN declared_email VARCHAR(150);
ALTER TABLE person_guardians ADD CONSTRAINT person_guardians_invitation_subject_unique UNIQUE (person_guardian_id,dependent_person_id);
CREATE TABLE person_access_invitations (
 invitation_id UUID PRIMARY KEY,
 person_id UUID NOT NULL REFERENCES people(person_id),
 representation_id UUID NOT NULL REFERENCES person_guardians(person_guardian_id),
 token_hash VARCHAR(64) NOT NULL UNIQUE,
 email VARCHAR(150) NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL,
 consumed_at TIMESTAMPTZ,
 CONSTRAINT person_access_invitation_subject_fk FOREIGN KEY (representation_id,person_id) REFERENCES person_guardians(person_guardian_id,dependent_person_id)
);
CREATE INDEX person_access_invitation_person_idx ON person_access_invitations(person_id);
DELETE FROM person_role_assignment_training_paths WHERE person_role_assignment_id IN (SELECT a.person_role_assignment_id FROM person_role_assignments a JOIN roles r ON r.role_id=a.role_id WHERE r.code='GUARDIAN');
DELETE FROM person_role_assignments WHERE role_id IN (SELECT role_id FROM roles WHERE code='GUARDIAN');
DELETE FROM role_permissions WHERE role_id IN (SELECT role_id FROM roles WHERE code='GUARDIAN');
DELETE FROM roles WHERE code='GUARDIAN';
CREATE UNIQUE INDEX person_access_invitation_pending_unique ON person_access_invitations(person_id) WHERE consumed_at IS NULL;

DELETE FROM role_permissions WHERE permission_id IN (SELECT permission_id FROM permissions WHERE code='institution:guardian-dependent:manage');
DELETE FROM permissions WHERE code='institution:guardian-dependent:manage';
