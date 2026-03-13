package com.example.BegaDiary.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.BegaDiary.Entity.SeatViewPhoto;
import com.example.BegaDiary.Entity.SeatViewPhoto.ClassificationLabel;
import com.example.BegaDiary.Entity.SeatViewPhoto.ModerationStatus;

@Repository
public interface SeatViewPhotoRepository extends JpaRepository<SeatViewPhoto, Long> {

    @Query("""
            SELECT sv FROM SeatViewPhoto sv
            JOIN FETCH sv.diary d
            WHERE d.id = :diaryId
              AND sv.userId = :userId
            ORDER BY sv.createdAt ASC
            """)
    List<SeatViewPhoto> findByDiaryIdAndUserId(@Param("diaryId") Long diaryId, @Param("userId") Long userId);

    @Query("""
            SELECT sv FROM SeatViewPhoto sv
            JOIN FETCH sv.diary d
            WHERE sv.id IN :ids
              AND d.id = :diaryId
              AND sv.userId = :userId
            """)
    List<SeatViewPhoto> findByIdsForOwner(
            @Param("ids") List<Long> ids,
            @Param("diaryId") Long diaryId,
            @Param("userId") Long userId);

    @Query("""
            SELECT sv FROM SeatViewPhoto sv
            JOIN FETCH sv.diary d
            WHERE d.stadium = :stadium
              AND (:section IS NULL OR :section = '' OR d.section = :section)
              AND sv.userSelected = true
              AND sv.moderationStatus = :status
              AND sv.adminLabel = :adminLabel
            ORDER BY COALESCE(sv.reviewedAt, sv.createdAt) DESC
            """)
    List<SeatViewPhoto> findApprovedPublicSeatViews(
            @Param("stadium") String stadium,
            @Param("section") String section,
            @Param("status") ModerationStatus status,
            @Param("adminLabel") ClassificationLabel adminLabel,
            Pageable pageable);

    @Query("""
            SELECT sv FROM SeatViewPhoto sv
            JOIN FETCH sv.diary d
            WHERE (:status IS NULL OR sv.moderationStatus = :status)
              AND (:stadium IS NULL OR :stadium = '' OR d.stadium = :stadium)
              AND (:aiLabel IS NULL OR sv.aiSuggestedLabel = :aiLabel)
              AND (:adminLabel IS NULL OR sv.adminLabel = :adminLabel)
              AND (:ticketVerified IS NULL OR d.ticketVerified = :ticketVerified)
            ORDER BY sv.createdAt DESC
            """)
    List<SeatViewPhoto> findForAdmin(
            @Param("status") ModerationStatus status,
            @Param("stadium") String stadium,
            @Param("aiLabel") ClassificationLabel aiLabel,
            @Param("adminLabel") ClassificationLabel adminLabel,
            @Param("ticketVerified") Boolean ticketVerified);

    @Query("""
            SELECT sv FROM SeatViewPhoto sv
            JOIN FETCH sv.diary d
            WHERE sv.id = :id
            """)
    Optional<SeatViewPhoto> findDetailById(@Param("id") Long id);

    @Query("""
            SELECT sv FROM SeatViewPhoto sv
            JOIN FETCH sv.diary d
            WHERE d.id = :diaryId
              AND sv.moderationStatus = :status
              AND sv.adminLabel = :adminLabel
            ORDER BY COALESCE(sv.reviewedAt, sv.createdAt) ASC
            """)
    List<SeatViewPhoto> findApprovedByDiaryId(
            @Param("diaryId") Long diaryId,
            @Param("status") ModerationStatus status,
            @Param("adminLabel") ClassificationLabel adminLabel);

    void deleteByDiary_Id(Long diaryId);
}
