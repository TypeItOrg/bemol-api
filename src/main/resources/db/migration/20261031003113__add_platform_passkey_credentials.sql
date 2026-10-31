CREATE TABLE platform_passkey_credentials (
  platform_passkey_credential_id UUID PRIMARY KEY,
  platform_account_id UUID NOT NULL REFERENCES platform_accounts(platform_account_id) ON DELETE CASCADE,
  credential_id TEXT NOT NULL UNIQUE,
  public_key_cose BYTEA NOT NULL,
  signature_count BIGINT NOT NULL DEFAULT 0,
  transports VARCHAR(255),
  backup_eligible BOOLEAN NOT NULL DEFAULT FALSE,
  backup_state BOOLEAN NOT NULL DEFAULT FALSE,
  uv_initialized BOOLEAN NOT NULL DEFAULT FALSE,
  user_handle BYTEA NOT NULL,
  attestation_object BYTEA,
  attestation_client_data_json BYTEA,
  label VARCHAR(100) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  last_used_at TIMESTAMP,
  revoked_at TIMESTAMP,
  CONSTRAINT platform_passkey_credentials_label_length CHECK (char_length(label) >= 1 AND char_length(label) <= 100)
);

CREATE INDEX platform_passkey_credentials_account_active_idx
  ON platform_passkey_credentials(platform_account_id)
  WHERE revoked_at IS NULL;

CREATE INDEX platform_passkey_credentials_account_id_idx
  ON platform_passkey_credentials(platform_account_id);

UPDATE platform_sessions SET active = FALSE, ended_at = CURRENT_TIMESTAMP WHERE active;
UPDATE platform_refresh_tokens SET revoked = TRUE WHERE NOT revoked;
