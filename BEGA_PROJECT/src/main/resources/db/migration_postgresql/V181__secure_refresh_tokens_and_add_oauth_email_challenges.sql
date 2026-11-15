-- Force re-login while replacing persisted bearer tokens with HMAC digests only.
DELETE FROM refresh_tokens;

ALTER TABLE refresh_tokens ADD COLUMN token_digest VARCHAR(64) NOT NULL;
ALTER TABLE refresh_tokens DROP COLUMN token;
CREATE UNIQUE INDEX uk_refresh_tokens_token_digest ON refresh_tokens(token_digest);

CREATE TABLE oauth_email_challenges (
    id BIGSERIAL PRIMARY KEY,
    challenge_id VARCHAR(36) NOT NULL,
    token_digest VARCHAR(64),
    provider VARCHAR(20) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    email VARCHAR(320),
    name VARCHAR(255),
    profile_image_url VARCHAR(2048),
    created_at TIMESTAMPTZ NOT NULL,
    expiry_date TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_oauth_email_chal_digest
    ON oauth_email_challenges(token_digest);
CREATE UNIQUE INDEX uk_oauth_email_chal_id
    ON oauth_email_challenges(challenge_id);
CREATE INDEX ix_oauth_email_chal_provider
    ON oauth_email_challenges(provider, provider_id);
