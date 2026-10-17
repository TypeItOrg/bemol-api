-- Metadata-only defaults preserve the append-only history without rewriting events.
ALTER TABLE audit_events
    ALTER COLUMN institution_id DROP NOT NULL,
    ALTER COLUMN actor_person_id DROP NOT NULL,
    ALTER COLUMN subject_person_id DROP NOT NULL,
    ADD COLUMN actor_kind VARCHAR(20) NOT NULL DEFAULT 'INSTITUTION',
    ADD COLUMN actor_platform_account_id UUID,
    ADD COLUMN origin VARCHAR(40),
    ADD COLUMN details JSONB NOT NULL DEFAULT '{}'::jsonb,
    DROP CONSTRAINT audit_events_on_behalf_consistent,
    ADD CONSTRAINT audit_events_actor_shape CHECK (
        (actor_kind = 'INSTITUTION' AND institution_id IS NOT NULL AND actor_person_id IS NOT NULL AND actor_platform_account_id IS NULL)
        OR (actor_kind = 'PLATFORM' AND actor_person_id IS NULL AND actor_platform_account_id IS NOT NULL)
        OR (actor_kind = 'ANONYMOUS' AND actor_person_id IS NULL AND actor_platform_account_id IS NULL)
    ),
    ADD CONSTRAINT audit_events_on_behalf_consistent CHECK (
        NOT acted_on_behalf OR (actor_kind = 'INSTITUTION' AND subject_person_id IS NOT NULL AND actor_person_id <> subject_person_id)
    ),
    ADD CONSTRAINT audit_events_details_object CHECK (jsonb_typeof(details) = 'object');

CREATE INDEX audit_events_global_timeline_idx ON audit_events (occurred_at DESC, audit_event_id DESC);
