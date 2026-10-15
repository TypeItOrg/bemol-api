ALTER TABLE person_guardians
    ADD CONSTRAINT person_guardians_institution_id_id_unique
    UNIQUE (institution_id, person_guardian_id);

ALTER TABLE person_guardian_attachments
    DROP CONSTRAINT person_guardian_attachments_link_fk,
    ADD CONSTRAINT person_guardian_attachments_link_institution_fk
    FOREIGN KEY (institution_id, person_guardian_id)
    REFERENCES person_guardians (institution_id, person_guardian_id);
