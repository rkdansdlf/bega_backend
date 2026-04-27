CREATE TABLE IF NOT EXISTS media_assets (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    domain VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    original_file_name VARCHAR(512),
    declared_content_type VARCHAR(128) NOT NULL,
    declared_bytes BIGINT NOT NULL,
    declared_width INTEGER NOT NULL,
    declared_height INTEGER NOT NULL,
    staging_object_key VARCHAR(2048) NOT NULL,
    object_key VARCHAR(2048),
    stored_content_type VARCHAR(128),
    stored_bytes BIGINT,
    stored_width INTEGER,
    stored_height INTEGER,
    upload_expires_at TIMESTAMP NOT NULL,
    finalized_at TIMESTAMP,
    derived_from_asset_id BIGINT REFERENCES media_assets(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS media_asset_links (
    asset_id BIGINT PRIMARY KEY REFERENCES media_assets(id) ON DELETE CASCADE,
    domain VARCHAR(32) NOT NULL,
    entity_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_media_assets_object_key
    ON media_assets (object_key);

CREATE UNIQUE INDEX IF NOT EXISTS uk_media_assets_derived
    ON media_assets (derived_from_asset_id);

CREATE INDEX IF NOT EXISTS idx_media_assets_owner_created
    ON media_assets (owner_user_id, created_at);

CREATE INDEX IF NOT EXISTS idx_media_assets_status_exp
    ON media_assets (status, upload_expires_at);

CREATE INDEX IF NOT EXISTS idx_media_assets_domain_status
    ON media_assets (domain, status);

CREATE INDEX IF NOT EXISTS idx_media_links_entity
    ON media_asset_links (domain, entity_id);
