-- V127: Create AI chat session/message/favorite tables for authenticated chatbot persistence (PostgreSQL)

CREATE TABLE IF NOT EXISTS ai_chat_session (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL,
    message_count INTEGER NOT NULL DEFAULT 0,
    last_message_preview VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_chat_message (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES ai_chat_session(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    verified BOOLEAN,
    cached BOOLEAN,
    intent VARCHAR(100),
    strategy VARCHAR(100),
    finish_reason VARCHAR(50),
    cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    error_code VARCHAR(100),
    planner_mode VARCHAR(50),
    planner_cache_hit BOOLEAN,
    tool_execution_mode VARCHAR(50),
    fallback_reason VARCHAR(100),
    metadata_json TEXT,
    citations_json TEXT,
    tool_calls_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_chat_favorite (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message_id BIGINT NOT NULL REFERENCES ai_chat_message(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_chat_fav_usr_msg
    ON ai_chat_favorite(user_id, message_id);

CREATE INDEX IF NOT EXISTS ix_ai_chat_session_user_last
    ON ai_chat_session(user_id, last_message_at DESC);

CREATE INDEX IF NOT EXISTS ix_ai_chat_msg_session_created
    ON ai_chat_message(session_id, created_at);

CREATE INDEX IF NOT EXISTS ix_ai_chat_fav_user_created
    ON ai_chat_favorite(user_id, created_at DESC);
