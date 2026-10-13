ALTER TABLE mate_seller_payout_profiles ALTER COLUMN provider_seller_id DROP NOT NULL;
ALTER TABLE media_assets ALTER COLUMN owner_user_id DROP NOT NULL;
ALTER TABLE party_applications ALTER COLUMN applicant_rating DROP NOT NULL;

CREATE TABLE mate_anonymous_review_aggregates (
    id BIGSERIAL PRIMARY KEY,
    reviewee_id BIGINT NOT NULL,
    rating_sum BIGINT NOT NULL,
    review_count BIGINT NOT NULL
);

CREATE INDEX idx_mate_anon_reviews_reviewee
    ON mate_anonymous_review_aggregates (reviewee_id);
