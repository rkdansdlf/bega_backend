-- V31: Increase refresh token column size
-- Resolves ORA-12899: value too large for column "ADMIN"."REFRESH_TOKENS"."TOKEN" (actual: 262, maximum: 255)

ALTER TABLE refresh_tokens MODIFY token VARCHAR2(1024);
