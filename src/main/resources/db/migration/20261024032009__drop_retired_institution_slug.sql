DROP INDEX institutions_contextual_search_idx;
DROP INDEX institutions_slug_search_trgm_idx;
ALTER TABLE institutions DROP CONSTRAINT institutions_slug_unique;
ALTER TABLE institutions DROP COLUMN slug;

CREATE INDEX institutions_contextual_search_idx
    ON institutions USING gin (bemol_search_vector(name));
