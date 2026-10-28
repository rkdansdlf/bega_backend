-- Hash legacy plaintext account deletion recovery tokens (UUID) in place.
-- Must match AccountDeletionService.hashRecoveryToken: SHA-256 over the UTF-8 bytes, lowercase hex.
-- Rows that already hold a 64-character digest do not match the UUID pattern and are left untouched.
UPDATE account_deletion_tokens
SET token = encode(sha256(convert_to(token, 'UTF8')), 'hex')
WHERE token ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';
