-- Require complete data on new writes without rewriting existing people.
ALTER TABLE people
    ADD CONSTRAINT people_nationality_required CHECK (nationality_country_id IS NOT NULL) NOT VALID,
    ADD CONSTRAINT people_phone_number_required
        CHECK (phone_number IS NOT NULL AND phone_number ~ '[^[:space:]]') NOT VALID;

ALTER TABLE person_guardians
    ADD COLUMN declared_phone_number varchar(30),
    ADD COLUMN declared_nationality_country_id uuid REFERENCES countries(country_id);
