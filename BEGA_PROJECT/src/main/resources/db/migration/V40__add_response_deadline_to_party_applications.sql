-- Migration V40: Add response_deadline to party_applications table
-- Resolves ORA-00904: "PA1_0"."RESPONSE_DEADLINE": 부적합한 식별자

ALTER TABLE party_applications ADD (
    response_deadline TIMESTAMP(6)
);

-- Add comment to the new column
COMMENT ON COLUMN party_applications.response_deadline IS 'The deadline for the host to respond to the application (usually 48 hours after creation)';
