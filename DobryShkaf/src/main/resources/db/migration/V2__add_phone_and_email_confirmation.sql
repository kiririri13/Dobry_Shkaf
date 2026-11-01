ALTER TABLE users
    ADD COLUMN phone              VARCHAR(16),
    ADD COLUMN email_confirmed_at TIMESTAMPTZ;

CREATE UNIQUE INDEX uq_users_phone ON users (phone);

CREATE TABLE email_confirmation_tokens (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_email_confirmation_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_email_confirmation_tokens_user_id ON email_confirmation_tokens (user_id);
