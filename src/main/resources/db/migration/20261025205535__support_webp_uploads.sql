ALTER TABLE institutions
    DROP CONSTRAINT institutions_logo_metadata_check,
    ADD CONSTRAINT institutions_logo_metadata_check CHECK (
        (logo_key IS NULL AND logo_content_type IS NULL AND logo_version IS NULL AND logo_size IS NULL)
        OR (logo_key IS NOT NULL AND logo_content_type IS NOT NULL
            AND logo_content_type IN ('image/png', 'image/jpeg', 'image/webp')
            AND logo_version IS NOT NULL AND logo_size BETWEEN 1 AND 2097152 AND logo_size IS NOT NULL)
    );

ALTER TABLE document_definitions
    DROP CONSTRAINT document_definitions_allowed_formats_check,
    ADD CONSTRAINT document_definitions_allowed_formats_check CHECK (
        jsonb_typeof(allowed_formats) = 'array' AND jsonb_array_length(allowed_formats) > 0
        AND allowed_formats <@ '["application/pdf","image/jpeg","image/png","image/webp"]'::jsonb
    );

ALTER TABLE enrollment_document_requirements
    DROP CONSTRAINT enrollment_document_requirements_allowed_formats_check,
    ADD CONSTRAINT enrollment_document_requirements_allowed_formats_check CHECK (
        jsonb_typeof(allowed_formats) = 'array' AND jsonb_array_length(allowed_formats) > 0
        AND allowed_formats <@ '["application/pdf","image/jpeg","image/png","image/webp"]'::jsonb
    );
