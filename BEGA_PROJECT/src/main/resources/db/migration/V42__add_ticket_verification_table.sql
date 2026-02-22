CREATE TABLE ticket_verifications (
    token VARCHAR2(36) NOT NULL PRIMARY KEY,
    ticket_date VARCHAR2(255),
    ticket_stadium VARCHAR2(255),
    home_team VARCHAR2(255),
    away_team VARCHAR2(255),
    game_id NUMBER(19),
    consumed NUMBER(1) DEFAULT 0 NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT chk_ticket_verifications_consumed CHECK (consumed IN (0, 1))
);

CREATE INDEX idx_ticket_verifications_expires ON ticket_verifications (expires_at);
