-- Migration: Add 'deleted' column to cheer_post table
-- Run this SQL against your PostgreSQL database

-- Step 1: Add column with default value (allows NULL temporarily)
ALTER TABLE cheer_post ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT false;

-- Step 2: Update existing rows to false
UPDATE cheer_post SET deleted = false WHERE deleted IS NULL;

-- Step 3: Set NOT NULL constraint
ALTER TABLE cheer_post ALTER COLUMN deleted SET NOT NULL;

-- Verify
SELECT COUNT(*) AS total, COUNT(deleted) AS with_deleted FROM cheer_post;
