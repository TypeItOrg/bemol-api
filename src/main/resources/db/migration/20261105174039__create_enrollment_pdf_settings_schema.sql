CREATE TABLE enrollment_pdf_settings (
    institution_id uuid PRIMARY KEY REFERENCES institutions(institution_id),
    title text NOT NULL,
    header_text text,
    introduction_text text,
    footer_text text,
    show_applicant_signature boolean NOT NULL,
    show_responsible_signature boolean NOT NULL,
    CONSTRAINT enrollment_pdf_settings_title_check CHECK (length(btrim(title)) BETWEEN 1 AND 150),
    CONSTRAINT enrollment_pdf_settings_header_check CHECK (header_text IS NULL OR length(header_text) <= 500),
    CONSTRAINT enrollment_pdf_settings_introduction_check CHECK (introduction_text IS NULL OR length(introduction_text) <= 1000),
    CONSTRAINT enrollment_pdf_settings_footer_check CHECK (footer_text IS NULL OR length(footer_text) <= 500)
);
