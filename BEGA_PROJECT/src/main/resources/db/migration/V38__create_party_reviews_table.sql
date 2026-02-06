-- Create party_reviews table for Phase 4.3: Review/Rating System
CREATE TABLE party_reviews (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    party_id NUMBER NOT NULL,
    reviewer_id NUMBER NOT NULL,
    reviewee_id NUMBER NOT NULL,
    rating NUMBER(1) NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment VARCHAR2(200),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_party_review UNIQUE (party_id, reviewer_id, reviewee_id),
    CONSTRAINT fk_party_review_party FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE CASCADE,
    CONSTRAINT fk_party_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_party_review_reviewee FOREIGN KEY (reviewee_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create indexes for performance
CREATE INDEX idx_party_reviews_party_id ON party_reviews(party_id);
CREATE INDEX idx_party_reviews_reviewee_id ON party_reviews(reviewee_id);
CREATE INDEX idx_party_reviews_reviewer_id ON party_reviews(reviewer_id);

-- Add comment
COMMENT ON TABLE party_reviews IS 'Party review and rating system - stores reviews written by participants after party completion';
COMMENT ON COLUMN party_reviews.party_id IS 'Reference to the party being reviewed';
COMMENT ON COLUMN party_reviews.reviewer_id IS 'User who wrote the review';
COMMENT ON COLUMN party_reviews.reviewee_id IS 'User being reviewed';
COMMENT ON COLUMN party_reviews.rating IS 'Rating from 1-5';
COMMENT ON COLUMN party_reviews.comment IS 'Optional review comment (max 200 chars)';
