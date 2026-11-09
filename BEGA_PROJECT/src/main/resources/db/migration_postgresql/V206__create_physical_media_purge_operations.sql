-- Durable operation records. Do not cascade deletes into the operation history.
ALTER TABLE physical_media_refs ADD CONSTRAINT uq_pmr_exact_binding
    UNIQUE (source_kind, source_id, source_generation, object_hash);

CREATE TABLE physical_media_purge_ops (
    operation_id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE physical_media_purge_sources (
    operation_id VARCHAR(36) NOT NULL,
    source_kind VARCHAR(32) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    source_generation VARCHAR(36) NOT NULL,
    object_hash VARCHAR(64) NOT NULL,
    CONSTRAINT pk_purge_sources PRIMARY KEY (source_kind, source_id, source_generation),
    CONSTRAINT fk_purge_src_op FOREIGN KEY (operation_id)
        REFERENCES physical_media_purge_ops (operation_id),
    CONSTRAINT fk_purge_src_ref FOREIGN KEY (source_kind, source_id, source_generation, object_hash)
        REFERENCES physical_media_refs (source_kind, source_id, source_generation, object_hash)
);

CREATE TABLE physical_media_purge_targets (
    operation_id VARCHAR(36) NOT NULL,
    object_hash VARCHAR(64) NOT NULL,
    outcome VARCHAR(8) NOT NULL,
    CONSTRAINT pk_purge_targets PRIMARY KEY (operation_id, object_hash),
    CONSTRAINT uq_purge_target_outcome UNIQUE (operation_id, object_hash, outcome),
    CONSTRAINT ck_purge_target_outcome CHECK (outcome IN ('SHARED', 'RESERVED')),
    CONSTRAINT fk_purge_target_op FOREIGN KEY (operation_id)
        REFERENCES physical_media_purge_ops (operation_id),
    CONSTRAINT fk_purge_target_object FOREIGN KEY (object_hash)
        REFERENCES physical_media_objects (object_hash)
);

CREATE TABLE physical_media_purge_owners (
    object_hash VARCHAR(64) PRIMARY KEY,
    operation_id VARCHAR(36) NOT NULL,
    outcome VARCHAR(8) DEFAULT 'RESERVED' NOT NULL,
    CONSTRAINT ck_purge_owner_reserved CHECK (outcome = 'RESERVED'),
    CONSTRAINT fk_purge_owner_target FOREIGN KEY (operation_id, object_hash, outcome)
        REFERENCES physical_media_purge_targets (operation_id, object_hash, outcome)
);
