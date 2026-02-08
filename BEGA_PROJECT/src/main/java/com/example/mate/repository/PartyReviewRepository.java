package com.example.mate.repository;

// Force IDE re-index

import com.example.mate.entity.PartyReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PartyReviewRepository extends JpaRepository<PartyReview, Long> {

    /**
     * 특정 파티에 대한 모든 리뷰 조회
     */
    List<PartyReview> findByPartyId(Long partyId);

    /**
     * 특정 사용자가 받은 모든 리뷰 조회
     */
    List<PartyReview> findByRevieweeId(Long revieweeId);

    /**
     * 중복 리뷰 방지를 위한 존재 여부 확인
     */
    boolean existsByPartyIdAndReviewerIdAndRevieweeId(Long partyId, Long reviewerId, Long revieweeId);

    /**
     * 특정 사용자의 평균 평점 계산
     */
    @Query("SELECT AVG(r.rating) FROM PartyReview r WHERE r.revieweeId = :userId")
    Double calculateAverageRating(@Param("userId") Long userId);
}
