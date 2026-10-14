ALTER TABLE document_definitions ADD COLUMN used_at timestamptz;

-- Backfill all existing links, including inactive and historic requirements.
UPDATE document_definitions d SET used_at = CURRENT_TIMESTAMP
WHERE EXISTS (SELECT 1 FROM training_path_document_requirements r WHERE r.document_id = d.id)
   OR EXISTS (SELECT 1 FROM enrollment_document_requirements r WHERE r.document_id = d.id);

CREATE FUNCTION remember_document_definition_usage() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  UPDATE document_definitions SET used_at = COALESCE(used_at, CURRENT_TIMESTAMP)
  WHERE id = NEW.document_id AND institution_id = NEW.institution_id;
  RETURN NEW;
END $$;
CREATE TRIGGER training_path_document_remember_usage AFTER INSERT ON training_path_document_requirements
FOR EACH ROW EXECUTE FUNCTION remember_document_definition_usage();
CREATE TRIGGER enrollment_document_remember_usage AFTER INSERT ON enrollment_document_requirements
FOR EACH ROW EXECUTE FUNCTION remember_document_definition_usage();

CREATE FUNCTION protect_document_definition_institution() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.used_at IS NOT NULL AND NEW.used_at IS DISTINCT FROM OLD.used_at THEN
    RAISE EXCEPTION 'Document usage history is immutable'
      USING ERRCODE = '23514', CONSTRAINT = 'document_definition_usage_immutable_check';
  END IF;
  IF NEW.institution_id IS DISTINCT FROM OLD.institution_id AND (
    OLD.used_at IS NOT NULL
    OR EXISTS (SELECT 1 FROM training_path_document_requirements r WHERE r.document_id = OLD.id)
    OR EXISTS (SELECT 1 FROM enrollment_document_requirements r WHERE r.document_id = OLD.id)
  ) THEN
    RAISE EXCEPTION 'Used document cannot change institution'
      USING ERRCODE = '23514', CONSTRAINT = 'document_definition_institution_change_check';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER document_definition_protect_institution BEFORE UPDATE OF institution_id, used_at ON document_definitions
FOR EACH ROW EXECUTE FUNCTION protect_document_definition_institution();
