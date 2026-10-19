ALTER TABLE user_sessions
    ADD COLUMN active_person_id uuid,
    ADD CONSTRAINT user_sessions_active_person_fk
        FOREIGN KEY (active_person_id) REFERENCES people (person_id);
