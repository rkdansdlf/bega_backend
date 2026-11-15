ALTER TABLE party_applications
    ADD COLUMN IF NOT EXISTS ticket_image_url_extended TEXT;
