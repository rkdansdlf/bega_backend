-- Add reservation_number column to parties table for ticket verification
ALTER TABLE parties ADD (reservation_number VARCHAR2(50));

-- Add comment
COMMENT ON COLUMN parties.reservation_number IS 'Ticket reservation number for verification';
