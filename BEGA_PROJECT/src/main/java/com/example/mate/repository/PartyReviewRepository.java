package com.example.mate.repository;

// Force IDE re-index

import com.example.mate.entity.PartyReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PartyReviewRepository extends JpaRepository<PartyReview, Long> {

    interface RevieweeRatingSummary {
        Long getRevieweeId();

        Double getAverageRating();

        Long getReviewCount();
    }

    interface RevieweeRatingTotals {
        Long getRevieweeId();

        Long getRatingSum();

        Long getReviewCount();
    }

    /**
     * 특정 파티에 대한 모든 리뷰 조회
     */
    List<PartyReview> findByPartyId(Long partyId);

    boolean existsByPartyId(Long partyId);

    /**
     * 특정 사용자가 받은 모든 리뷰 조회
     */
    List<PartyReview> findByRevieweeId(Long revieweeId);

    List<PartyReview> findByReviewerIdOrRevieweeId(Long reviewerId, Long revieweeId);

    List<PartyReview> findTop2ByRevieweeIdOrderByCreatedAtDesc(Long revieweeId);

    /**
     * 중복 리뷰 방지를 위한 존재 여부 확인
     */
    boolean existsByPartyIdAndReviewerIdAndRevieweeId(Long partyId, Long reviewerId, Long revieweeId);

    /**
     * 특정 사용자의 평균 평점 계산
     */
    @Query("SELECT AVG(r.rating) FROM PartyReview r WHERE r.revieweeId = :userId")
    Double calculateAverageRating(@Param("userId") Long userId);

    @Query("""
            SELECT r.revieweeId AS revieweeId,
                   SUM(r.rating) AS ratingSum,
                   COUNT(r.id) AS reviewCount
              FROM PartyReview r
             WHERE r.revieweeId IN :revieweeIds
             GROUP BY r.revieweeId
            """)
    List<RevieweeRatingTotals> findRatingTotalsByRevieweeIds(@Param("revieweeIds") Collection<Long> revieweeIds);

    @Query(value = """
            SELECT combined.reviewee_id AS revieweeId,
                   SUM(combined.rating_sum) AS ratingSum,
                   SUM(combined.review_count) AS reviewCount
              FROM (
                    SELECT reviewee_id,
                           SUM(rating) AS rating_sum,
                           COUNT(id) AS review_count
                      FROM party_reviews
                     WHERE reviewee_id IN (:revieweeIds)
                     GROUP BY reviewee_id
                    UNION ALL
                    SELECT reviewee_id,
                           SUM(rating_sum) AS rating_sum,
                           SUM(review_count) AS review_count
                      FROM mate_anonymous_review_aggregates
                     WHERE reviewee_id IN (:revieweeIds)
                     GROUP BY reviewee_id
                   ) combined
             GROUP BY combined.reviewee_id
            """, nativeQuery = true)
    List<RevieweeRatingTotals> findCombinedRatingTotalsByRevieweeIds(
            @Param("revieweeIds") Collection<Long> revieweeIds);

    @Query("""
            SELECT r.revieweeId AS revieweeId,
                   AVG(r.rating) AS averageRating,
                   COUNT(r.id) AS reviewCount
              FROM PartyReview r
             WHERE r.revieweeId IN :revieweeIds
             GROUP BY r.revieweeId
            """)
    List<RevieweeRatingSummary> findRatingSummariesByRevieweeIds(
            @Param("revieweeIds") Collection<Long> revieweeIds);
}
