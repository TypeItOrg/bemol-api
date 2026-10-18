ALTER TABLE person_guardians ADD COLUMN declared_birth_date DATE;

-- Old requests have no retained declared date; do not copy protected registered data into them.
