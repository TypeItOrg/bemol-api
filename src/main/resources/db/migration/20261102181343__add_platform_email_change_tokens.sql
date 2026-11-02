CREATE TABLE platform_email_change_tokens (
    id UUID PRIMARY KEY,
    platform_account_id UUID NOT NULL,
    previous_email VARCHAR(150) NOT NULL,
    new_email VARCHAR(150) NOT NULL,
    password_fingerprint VARCHAR(64) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    CONSTRAINT platform_email_change_account_fk
        FOREIGN KEY (platform_account_id) REFERENCES platform_accounts (platform_account_id),
    CONSTRAINT platform_email_change_account_unique UNIQUE (platform_account_id),
    CONSTRAINT platform_email_change_token_unique UNIQUE (token_hash),
    CONSTRAINT platform_email_change_expiry_check CHECK (expires_at > issued_at),
    CONSTRAINT platform_email_change_email_check CHECK (new_email <> previous_email)
);
