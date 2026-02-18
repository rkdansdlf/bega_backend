-- V32: Add session metadata columns to refresh token table
ALTER TABLE refresh_tokens
    ADD (
        device_type VARCHAR2(32),
        device_label VARCHAR2(255),
        browser VARCHAR2(64),
        os VARCHAR2(64),
        ip VARCHAR2(64),
        last_seen_at TIMESTAMP
    );
