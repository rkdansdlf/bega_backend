UPDATE cheer_post p
SET likecount = COALESCE((
    SELECT COUNT(*)
    FROM cheer_post_like l
    WHERE l.post_id = p.id
), 0);
