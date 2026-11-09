-- Preserve each source generation so delayed retries cannot resurrect a retired reference.
ALTER TABLE physical_media_refs ADD COLUMN retired BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_pmr_object_retired ON physical_media_refs (object_hash, retired);
