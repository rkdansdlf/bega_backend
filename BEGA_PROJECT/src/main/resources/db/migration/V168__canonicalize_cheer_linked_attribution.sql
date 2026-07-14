UPDATE cheer_post
SET share_mode = 'INTERNAL_REPOST',
    source_url = NULL,
    source_title = NULL,
    source_author = NULL,
    source_license = NULL,
    source_license_url = NULL,
    source_changed_note = NULL,
    source_snapshot_type = NULL
WHERE posttype IN ('CHECKIN', 'RECRUITMENT');
