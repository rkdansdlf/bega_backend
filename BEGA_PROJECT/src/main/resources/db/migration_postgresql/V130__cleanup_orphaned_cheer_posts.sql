DELETE FROM cheer_post post
WHERE NOT EXISTS (
    SELECT 1
    FROM users u
    WHERE u.id = post.author_id
);
