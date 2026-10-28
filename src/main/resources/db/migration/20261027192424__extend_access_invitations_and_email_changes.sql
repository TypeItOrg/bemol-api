ALTER TABLE person_access_invitations ADD COLUMN platform_issuer_account_id uuid REFERENCES platform_accounts(platform_account_id);
ALTER TABLE person_access_invitations ADD COLUMN purpose varchar(24) NOT NULL DEFAULT 'INITIAL_ACCESS';
ALTER TABLE person_access_invitations ADD COLUMN issued_at timestamptz;
ALTER TABLE person_access_invitations ADD COLUMN cancelled_at timestamptz;
ALTER TABLE person_access_invitations ADD COLUMN expected_email varchar(150);
ALTER TABLE person_access_invitations ADD COLUMN mail_outcome varchar(16);
UPDATE person_access_invitations i SET issued_at = i.expires_at - interval '24 hours', expected_email = p.email FROM people p WHERE p.person_id = i.person_id;
ALTER TABLE person_access_invitations ALTER COLUMN issued_at SET NOT NULL;
ALTER TABLE person_access_invitations DROP CONSTRAINT person_access_invitation_origin_check;
ALTER TABLE person_access_invitations ADD CONSTRAINT person_access_invitation_origin_check CHECK (
    (representation_id IS NOT NULL AND institutional_issuer_person_id IS NULL AND platform_issuer_account_id IS NULL AND issuing_institution_id IS NULL AND purpose = 'INITIAL_ACCESS') OR
    (representation_id IS NULL AND issuing_institution_id IS NOT NULL AND num_nonnulls(institutional_issuer_person_id,platform_issuer_account_id) = 1));
ALTER TABLE person_access_invitations ADD CONSTRAINT person_access_invitation_purpose_check CHECK (purpose IN ('INITIAL_ACCESS','AUTONOMY'));
ALTER TABLE person_access_invitations ADD CONSTRAINT person_access_invitation_resolution_check CHECK (consumed_at IS NULL OR cancelled_at IS NULL);
DROP INDEX person_access_invitation_pending_unique;
CREATE UNIQUE INDEX person_access_invitation_pending_unique ON person_access_invitations(person_id) WHERE consumed_at IS NULL AND cancelled_at IS NULL;
ALTER TABLE envers_poc_person_access_invitations_aud ADD COLUMN platform_issuer_account_id uuid, ADD COLUMN purpose varchar(24), ADD COLUMN issued_at timestamptz, ADD COLUMN cancelled_at timestamptz;
CREATE TABLE account_email_change_tokens (
 id uuid PRIMARY KEY,
 user_id uuid NOT NULL UNIQUE REFERENCES users(user_id),
 actor_person_id uuid REFERENCES people(person_id),
 actor_platform_account_id uuid REFERENCES platform_accounts(platform_account_id),
 previous_email varchar(150) NOT NULL,
 new_email varchar(150) NOT NULL,
 token_hash varchar(64) NOT NULL UNIQUE,
 issued_at timestamptz NOT NULL,
 expires_at timestamptz NOT NULL,
 used_at timestamptz,
 CONSTRAINT account_email_change_actor_check CHECK (num_nonnulls(actor_person_id,actor_platform_account_id) = 1)
);
