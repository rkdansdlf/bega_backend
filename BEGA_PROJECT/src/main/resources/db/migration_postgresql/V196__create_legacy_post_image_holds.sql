-- Private reconciliation records survive deletion of the original image or post.
CREATE TABLE legacy_post_image_holds (
    post_image_id BIGINT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    storage_path VARCHAR(2048) NOT NULL,
    logical_bucket VARCHAR(255),
    report_protected BOOLEAN NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'UNRESOLVED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_lpih_status CHECK (status = 'UNRESOLVED')
);

CREATE INDEX idx_lpih_owner ON legacy_post_image_holds (owner_user_id, post_image_id);
