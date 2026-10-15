-- Do not backfill from people: pending requests must not disclose stored identity.
ALTER TABLE person_guardians
    ADD COLUMN declared_first_name VARCHAR(255),
    ADD COLUMN declared_last_name VARCHAR(255);

CREATE INDEX idx_person_guardians_tutor_list
    ON person_guardians (institution_id, tutor_person_id, created_at, person_guardian_id)
    WHERE status IN ('PENDING', 'ACTIVE', 'REJECTED');
