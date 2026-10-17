ALTER TABLE person_access_invitations ALTER COLUMN representation_id DROP NOT NULL;
ALTER TABLE person_access_invitations ADD COLUMN institutional_issuer_person_id UUID;
ALTER TABLE person_access_invitations ADD COLUMN issuing_institution_id UUID;
ALTER TABLE person_access_invitations
    ADD CONSTRAINT person_access_invitation_origin_check CHECK (
        (representation_id IS NOT NULL AND institutional_issuer_person_id IS NULL AND issuing_institution_id IS NULL)
        OR (representation_id IS NULL AND institutional_issuer_person_id IS NOT NULL AND issuing_institution_id IS NOT NULL)
    ),
    ADD CONSTRAINT person_access_invitation_institutional_subject_fk
        FOREIGN KEY (issuing_institution_id, person_id) REFERENCES people(institution_id, person_id),
    ADD CONSTRAINT person_access_invitation_institutional_issuer_fk
        FOREIGN KEY (issuing_institution_id, institutional_issuer_person_id) REFERENCES people(institution_id, person_id);
