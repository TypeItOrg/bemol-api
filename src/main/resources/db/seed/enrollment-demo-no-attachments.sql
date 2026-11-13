-- Drop the attachment rows created by this seed; files uploaded later use other paths.
DELETE FROM enrollment_attachments WHERE storage_path LIKE :'seed_asset_prefix' || '/%';
DELETE FROM person_guardian_attachments WHERE storage_path LIKE :'seed_asset_prefix' || '/%';
