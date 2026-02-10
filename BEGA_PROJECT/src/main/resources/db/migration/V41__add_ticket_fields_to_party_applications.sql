-- V41: Add ticket verification fields to party_applications table
-- Supports the new Mate Ticket Verification feature

ALTER TABLE party_applications ADD (
    ticket_verified NUMBER(1) DEFAULT 0 NOT NULL,
    ticket_image_url VARCHAR2(500)
);
