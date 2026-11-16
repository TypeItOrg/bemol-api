-- "Imágenes" means JPEG, PNG and WebP. Documents created before WebP existed only list
-- JPEG/PNG, so applicants could not attach a WebP image. Complete the image group.
UPDATE document_definitions
SET allowed_formats = (
        SELECT jsonb_agg(format ORDER BY format)
        FROM (SELECT DISTINCT jsonb_array_elements_text(
                allowed_formats || '["image/jpeg","image/png","image/webp"]'::jsonb) AS format) formats)
WHERE allowed_formats ?| ARRAY['image/jpeg', 'image/png', 'image/webp']
  AND NOT allowed_formats @> '["image/jpeg","image/png","image/webp"]'::jsonb;

-- Draft snapshots still follow their catalog document; submitted ones stay frozen.
UPDATE enrollment_document_requirements r
SET allowed_formats = d.allowed_formats
FROM document_definitions d, enrollment_applications a
WHERE d.id = r.document_id
  AND a.enrollment_application_id = r.application_id
  AND a.status = 'DRAFT'
  AND r.origin = 'ORIGINAL'
  AND r.allowed_formats IS DISTINCT FROM d.allowed_formats;
