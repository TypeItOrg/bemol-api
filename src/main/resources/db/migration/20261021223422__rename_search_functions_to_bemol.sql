-- Renaming preserves function OIDs and the existing expression indexes. SQL-standard
-- RETURN bodies track dependencies, so their references follow these renames too.
ALTER FUNCTION boero_normalize_text(text) RENAME TO bemol_normalize_text;
ALTER FUNCTION boero_search_vector(text) RENAME TO bemol_search_vector;
ALTER FUNCTION boero_search_rank(text, text, text) RENAME TO bemol_search_rank;
