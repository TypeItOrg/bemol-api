ALTER TABLE person_guardians ADD COLUMN declared_document_number varchar(8);
ALTER TABLE person_guardians ADD COLUMN request_mode varchar(20) NOT NULL DEFAULT 'NEW';
UPDATE person_guardians g SET declared_document_number = p.document_number FROM people p WHERE p.person_id = g.dependent_person_id;
ALTER TABLE person_guardians ALTER COLUMN declared_document_number SET NOT NULL;
ALTER TABLE person_guardians ALTER COLUMN dependent_person_id DROP NOT NULL;
ALTER TABLE person_guardians ADD CONSTRAINT person_guardians_active_subject_check CHECK (status <> 'ACTIVE' OR dependent_person_id IS NOT NULL);
ALTER TABLE person_guardians ADD CONSTRAINT person_guardians_request_mode_check CHECK (request_mode IN ('NEW','EXISTING'));
CREATE UNIQUE INDEX person_guardians_pending_document_unique ON person_guardians(institution_id,tutor_person_id,declared_document_number) WHERE status = 'PENDING';
