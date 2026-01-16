-- Create cheer_post_bookmark table
CREATE TABLE IF NOT EXISTS cheer_post_bookmark (
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT fk_cheer_post_bookmark_post FOREIGN KEY (post_id) REFERENCES cheer_post (id),
    CONSTRAINT fk_cheer_post_bookmark_user FOREIGN KEY (user_id) REFERENCES security.users (id)
);

-- Create cheer_post_reports table
CREATE TABLE IF NOT EXISTS cheer_post_reports (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_cheer_post_report_post FOREIGN KEY (post_id) REFERENCES cheer_post (id),
    CONSTRAINT fk_cheer_post_report_reporter FOREIGN KEY (reporter_id) REFERENCES security.users (id)
);
