-- Private reconciliation records survive deletion of the original image or post.
CREATE TABLE legacy_post_image_holds (
    post_image_id NUMBER(19) PRIMARY KEY,
    owner_user_id NUMBER(19) NOT NULL,
    post_id NUMBER(19) NOT NULL,
    storage_path VARCHAR2(2048) NOT NULL,
    logical_bucket VARCHAR2(255),
    report_protected NUMBER(1) NOT NULL,
    status VARCHAR2(24) DEFAULT 'UNRESOLVED' NOT NULL,
    created_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT ck_lpih_report CHECK (report_protected IN (0, 1)),
    CONSTRAINT ck_lpih_status CHECK (status = 'UNRESOLVED')
);

CREATE INDEX idx_lpih_owner ON legacy_post_image_holds (owner_user_id, post_image_id);
