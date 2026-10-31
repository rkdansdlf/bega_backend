package com.example.mate.repository;

import com.example.mate.entity.AnonymousPartyReviewAggregate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnonymousPartyReviewAggregateRepository
        extends JpaRepository<AnonymousPartyReviewAggregate, Long> {

    interface RatingTotals {
        Long getRevieweeId();

        Long getRatingSum();

        Long getReviewCount();
    }

    @Query("""
            SELECT a.revieweeId AS revieweeId,
                   SUM(a.ratingSum) AS ratingSum,
                   SUM(a.reviewCount) AS reviewCount
              FROM AnonymousPartyReviewAggregate a
             WHERE a.revieweeId IN :revieweeIds
             GROUP BY a.revieweeId
            """)
    List<RatingTotals> findRatingTotalsByRevieweeIds(@Param("revieweeIds") Collection<Long> revieweeIds);
}
