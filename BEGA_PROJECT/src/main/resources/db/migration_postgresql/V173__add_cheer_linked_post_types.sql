ALTER TABLE cheer_post ADD COLUMN IF NOT EXISTS diary_id BIGINT;
ALTER TABLE cheer_post ADD COLUMN IF NOT EXISTS party_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_cheer_post_diary'
          AND conrelid = 'cheer_post'::regclass
    ) THEN
        ALTER TABLE cheer_post ADD CONSTRAINT fk_cheer_post_diary
            FOREIGN KEY (diary_id) REFERENCES bega_diary(id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_cheer_post_party'
          AND conrelid = 'cheer_post'::regclass
    ) THEN
        ALTER TABLE cheer_post ADD CONSTRAINT fk_cheer_post_party
            FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_cheer_post_link_type'
          AND conrelid = 'cheer_post'::regclass
    ) THEN
        ALTER TABLE cheer_post ADD CONSTRAINT ck_cheer_post_link_type CHECK (
            (posttype IN ('NORMAL', 'NOTICE') AND diary_id IS NULL AND party_id IS NULL)
            OR (posttype = 'CHECKIN' AND party_id IS NULL)
            OR (posttype = 'RECRUITMENT' AND diary_id IS NULL)
        );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_cheer_post_diary ON cheer_post (diary_id);
CREATE INDEX IF NOT EXISTS idx_cheer_post_party ON cheer_post (party_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cheer_post_active_diary
    ON cheer_post (diary_id)
    WHERE diary_id IS NOT NULL AND deleted = FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_cheer_post_active_party
    ON cheer_post (party_id)
    WHERE party_id IS NOT NULL AND deleted = FALSE;
