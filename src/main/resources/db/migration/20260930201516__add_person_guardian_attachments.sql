-- Supporting documents (birth certificate, guardianship ruling, ...) a tutor attaches to a
-- guardianship request so the institution can validate it.

CREATE TABLE person_guardian_attachments (
    person_guardian_attachment_id UUID NOT NULL,
    institution_id                UUID NOT NULL,
    person_guardian_id            UUID NOT NULL,
    original_file_name            VARCHAR(255) NOT NULL,
    storage_path                  VARCHAR(500) NOT NULL,
    content_type                  VARCHAR(100) NOT NULL,
    file_size                     BIGINT NOT NULL,
    created_at                    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at                    TIMESTAMP(6) WITH TIME ZONE NOT NULL,

    CONSTRAINT person_guardian_attachments_pkey PRIMARY KEY (person_guardian_attachment_id),
    CONSTRAINT person_guardian_attachments_institution_fk
        FOREIGN KEY (institution_id) REFERENCES institutions (institution_id),
    CONSTRAINT person_guardian_attachments_link_fk
        FOREIGN KEY (person_guardian_id) REFERENCES person_guardians (person_guardian_id)
);

CREATE INDEX person_guardian_attachments_link_idx
    ON person_guardian_attachments (person_guardian_id);
