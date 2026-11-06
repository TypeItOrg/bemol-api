ALTER TABLE enrollment_pdf_settings DROP CONSTRAINT enrollment_pdf_settings_title_check;
ALTER TABLE enrollment_pdf_settings ADD CONSTRAINT enrollment_pdf_settings_title_check
    CHECK (length(title) BETWEEN 1 AND 150 AND title ~ '[^[:space:]]');
