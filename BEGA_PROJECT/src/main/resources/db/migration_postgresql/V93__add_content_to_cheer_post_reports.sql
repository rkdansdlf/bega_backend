-- Add missing content column for CheerPostReport mapping
ALTER TABLE cheer_post_reports 
ADD COLUMN content text;
