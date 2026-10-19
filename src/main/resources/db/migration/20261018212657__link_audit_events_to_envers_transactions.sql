-- Nullable correlation preserves append-only events and revisions without guessing old links.
ALTER TABLE audit_events ADD COLUMN transaction_id UUID;
ALTER TABLE envers_poc_revisions ADD COLUMN transaction_id UUID;
CREATE UNIQUE INDEX envers_poc_revisions_transaction_idx
    ON envers_poc_revisions(transaction_id) WHERE transaction_id IS NOT NULL;
