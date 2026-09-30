-- Append-only log of actions, including those a person performs on behalf of another
-- (e.g. a tutor acting for a dependent). People are plain ids without foreign keys so the
-- log is never blocked by, nor cascades from, changes to the people table.
CREATE TABLE audit_events (
    audit_event_id    UUID NOT NULL,
    institution_id    UUID NOT NULL,
    actor_person_id   UUID NOT NULL,
    subject_person_id UUID NOT NULL,
    action            VARCHAR(60) NOT NULL,
    entity_type       VARCHAR(40) NOT NULL,
    entity_id         UUID NOT NULL,
    acted_on_behalf   BOOLEAN NOT NULL,
    request_id        VARCHAR(100),
    occurred_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,

    CONSTRAINT audit_events_pkey PRIMARY KEY (audit_event_id),
    CONSTRAINT audit_events_institution_fk
        FOREIGN KEY (institution_id) REFERENCES institutions (institution_id),
    CONSTRAINT audit_events_on_behalf_consistent
        CHECK (acted_on_behalf = (actor_person_id <> subject_person_id))
);

CREATE INDEX audit_events_subject_idx
    ON audit_events (institution_id, subject_person_id, occurred_at DESC);
CREATE INDEX audit_events_entity_idx
    ON audit_events (institution_id, entity_id, occurred_at DESC);
CREATE INDEX audit_events_actor_idx
    ON audit_events (institution_id, actor_person_id, occurred_at DESC);

-- Append-only: history can be read and extended, never rewritten.
CREATE FUNCTION audit_events_reject_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_events_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_reject_change();
