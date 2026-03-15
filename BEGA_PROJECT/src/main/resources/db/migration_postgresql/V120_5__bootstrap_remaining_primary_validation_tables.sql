-- Bootstrap remaining primary-validation tables after Flyway-compatible schema sync.
-- These tables are absent on fresh PostgreSQL dev baselines but required by JPA validate.

CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    old_value VARCHAR(100),
    new_value VARCHAR(100),
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(500) NOT NULL,
    related_id BIGINT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    createdat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS player_movements (
    id BIGSERIAL PRIMARY KEY,
    movement_date DATE NOT NULL,
    section VARCHAR(50) NOT NULL,
    team_code VARCHAR(20) NOT NULL,
    player_name VARCHAR(100) NOT NULL,
    summary VARCHAR(300),
    details TEXT,
    contract_term VARCHAR(100),
    contract_value VARCHAR(120),
    option_details VARCHAR(300),
    counterparty_team VARCHAR(50),
    counterparty_details VARCHAR(500),
    source_label VARCHAR(100),
    source_url VARCHAR(500),
    announced_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cheer_post (
    id BIGSERIAL PRIMARY KEY,
    team_id VARCHAR(255) NOT NULL,
    posttype VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    author_id BIGINT NOT NULL,
    content TEXT,
    likecount INTEGER NOT NULL DEFAULT 0,
    commentcount INTEGER NOT NULL DEFAULT 0,
    views INTEGER NOT NULL DEFAULT 0,
    repostcount INTEGER NOT NULL DEFAULT 0,
    repost_of_id BIGINT,
    repost_type VARCHAR(10),
    share_mode VARCHAR(24) NOT NULL DEFAULT 'INTERNAL_REPOST',
    source_url VARCHAR(1024),
    source_title VARCHAR(300),
    source_author VARCHAR(200),
    source_license VARCHAR(120),
    source_license_url VARCHAR(1024),
    source_changed_note VARCHAR(1200),
    source_snapshot_type VARCHAR(80),
    createdat TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updatedat TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_cheer_post_team_id
        FOREIGN KEY (team_id)
        REFERENCES teams (team_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_cheer_post_author_id
        FOREIGN KEY (author_id)
        REFERENCES users (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_cheer_post_repost_of_id
        FOREIGN KEY (repost_of_id)
        REFERENCES cheer_post (id)
        ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS cheer_comment (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    parent_comment_id BIGINT,
    like_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_cheer_comment_post_id
        FOREIGN KEY (post_id)
        REFERENCES cheer_post (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_cheer_comment_author_id
        FOREIGN KEY (author_id)
        REFERENCES users (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_cheer_comment_parent_id
        FOREIGN KEY (parent_comment_id)
        REFERENCES cheer_comment (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS cheer_post_like (
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    createdat TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT fk_cheer_post_like_post_id
        FOREIGN KEY (post_id)
        REFERENCES cheer_post (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_cheer_post_like_user_id
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS cheer_comment_like (
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    createdat TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (comment_id, user_id),
    CONSTRAINT fk_cheer_comment_like_comment_id
        FOREIGN KEY (comment_id)
        REFERENCES cheer_comment (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_cheer_comment_like_user_id
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS cheer_post_bookmark (
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT fk_cheer_post_bookmark_post_id
        FOREIGN KEY (post_id)
        REFERENCES cheer_post (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_cheer_post_bookmark_user_id
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS cheer_post_repost (
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT fk_cheer_post_repost_post_id
        FOREIGN KEY (post_id)
        REFERENCES cheer_post (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_cheer_post_repost_user_id
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS cheer_post_reports (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    description VARCHAR(32600),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    admin_action VARCHAR(30),
    admin_memo VARCHAR(1000),
    handled_by BIGINT,
    handled_at TIMESTAMP,
    evidence_url VARCHAR(1024),
    requested_action VARCHAR(64),
    appeal_status VARCHAR(24) DEFAULT 'NONE',
    appeal_reason VARCHAR(1200),
    appeal_count INTEGER NOT NULL DEFAULT 0,
    createdat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cheer_post_reports_post_id
        FOREIGN KEY (post_id)
        REFERENCES cheer_post (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_cheer_post_reports_reporter_id
        FOREIGN KEY (reporter_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS cheer_battle_log (
    id BIGSERIAL PRIMARY KEY,
    game_id VARCHAR(255) NOT NULL,
    team_id VARCHAR(255) NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    voted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS cheer_battle_votes (
    game_id VARCHAR(255) NOT NULL,
    team_id VARCHAR(255) NOT NULL,
    vote_count INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (game_id, team_id)
);

CREATE TABLE IF NOT EXISTS post_images (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    storage_path VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    bytes BIGINT NOT NULL,
    is_thumbnail BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_post_images_post_id
        FOREIGN KEY (post_id)
        REFERENCES cheer_post (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_read_createdat
    ON notifications (user_id, is_read, createdat DESC);

CREATE INDEX IF NOT EXISTS idx_player_movements_team_date
    ON player_movements (team_code, movement_date DESC);

CREATE INDEX IF NOT EXISTS idx_cheer_post_type_created_at
    ON cheer_post (posttype, createdat DESC);

CREATE INDEX IF NOT EXISTS idx_cheer_team_post_type_created_at
    ON cheer_post (team_id, posttype, createdat DESC);

CREATE INDEX IF NOT EXISTS idx_cheer_post_author_created
    ON cheer_post (author_id, createdat DESC);

CREATE INDEX IF NOT EXISTS idx_cheer_comment_post_created
    ON cheer_comment (post_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_cheer_post_like_postid
    ON cheer_post_like (post_id);

CREATE INDEX IF NOT EXISTS idx_cheer_comment_like_cid
    ON cheer_comment_like (comment_id);

CREATE INDEX IF NOT EXISTS idx_cheer_post_reports_status_createdat
    ON cheer_post_reports (status, createdat DESC);

CREATE INDEX IF NOT EXISTS idx_cheer_post_reports_reporter_post_createdat
    ON cheer_post_reports (reporter_id, post_id, createdat DESC);

CREATE INDEX IF NOT EXISTS idx_post_images_post_id
    ON post_images (post_id);
