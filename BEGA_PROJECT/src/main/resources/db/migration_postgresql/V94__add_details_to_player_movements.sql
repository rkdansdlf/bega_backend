-- Add missing details column for PlayerMovement mapping
ALTER TABLE player_movements 
ADD COLUMN details text;
