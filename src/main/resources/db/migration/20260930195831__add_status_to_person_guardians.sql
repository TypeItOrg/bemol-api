-- A guardianship link is now a request the institution must resolve before the tutor
-- can represent the person. Links that existed before this change were implicitly approved.

ALTER TABLE person_guardians
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN resolved_by_person_id UUID,
    ADD COLUMN resolved_at TIMESTAMP(6) WITH TIME ZONE;

-- New links are requested as pending; the ACTIVE default only served the backfill above.
ALTER TABLE person_guardians
    ALTER COLUMN status SET DEFAULT 'PENDING';

ALTER TABLE person_guardians
    ADD CONSTRAINT person_guardians_status_check
        CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED', 'ENDED')),
    ADD CONSTRAINT person_guardians_resolved_by_institution_fk
        FOREIGN KEY (institution_id, resolved_by_person_id)
        REFERENCES people (institution_id, person_id);

-- Only one request per tutor and person may be open; rejected or ended ones allow asking again.
ALTER TABLE person_guardians
    DROP CONSTRAINT person_guardians_unique;

CREATE UNIQUE INDEX person_guardians_open_unique
    ON person_guardians (institution_id, tutor_person_id, dependent_person_id)
    WHERE status IN ('PENDING', 'ACTIVE');

CREATE INDEX person_guardians_status_idx
    ON person_guardians (institution_id, status);
