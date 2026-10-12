package com.example.BegaDiary.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.BegaDiary.Entity.SeatViewPhoto;
import com.example.BegaDiary.Entity.SeatViewPhoto.ClassificationLabel;
import com.example.BegaDiary.Entity.SeatViewPhoto.ModerationStatus;

public interface SeatViewPhotoRepository extends JpaRepository<SeatViewPhoto, Long> {

    @Query("""
            SELECT sv FROM SeatViewPhoto sv
            LEFT JOIN FETCH sv.diary d
            WHERE d.id = :diaryId
              AND sv.userId = :userId
            ORDER BY sv.createdAt ASC
            """)
    List<SeatViewPhoto> findByDiaryIdAndUserId(@Param("diaryId") Long diaryId, @Param("userId") Long userId);

    @Query("""
            SELECT sv FROM SeatViewPhoto sv
            LEFT JOIN FETCH sv.diary d
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
            LEFT JOIN FETCH sv.diary d
            WHERE COALESCE(sv.stadium, d.stadium) = :stadium
              AND (:section IS NULL OR :section = '' OR COALESCE(sv.section, d.section) = :section)
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

    @Query(value = """
            SELECT sv FROM SeatViewPhoto sv
            LEFT JOIN FETCH sv.diary d
            WHERE (:status IS NULL OR sv.moderationStatus = :status)
              AND (:stadium IS NULL OR :stadium = '' OR COALESCE(sv.stadium, d.stadium) = :stadium)
              AND (:aiLabel IS NULL OR sv.aiSuggestedLabel = :aiLabel)
              AND (:adminLabel IS NULL OR sv.adminLabel = :adminLabel)
              AND (:ticketVerified IS NULL OR COALESCE(d.ticketVerified, false) = :ticketVerified)
            ORDER BY CASE WHEN sv.moderationStatus = :pendingStatus THEN 0
                          WHEN sv.moderationStatus IS NULL THEN 1 ELSE 2 END,
                     CASE WHEN d.diaryDate IS NULL THEN 1 ELSE 0 END,
                     d.diaryDate DESC,
                     sv.id DESC
            """,
            countQuery = """
                    SELECT COUNT(sv) FROM SeatViewPhoto sv
                    LEFT JOIN sv.diary d
                    WHERE (:status IS NULL OR sv.moderationStatus = :status)
                      AND (:stadium IS NULL OR :stadium = '' OR COALESCE(sv.stadium, d.stadium) = :stadium)
                      AND (:aiLabel IS NULL OR sv.aiSuggestedLabel = :aiLabel)
                      AND (:adminLabel IS NULL OR sv.adminLabel = :adminLabel)
                      AND (:ticketVerified IS NULL OR COALESCE(d.ticketVerified, false) = :ticketVerified)
                    """)
    Page<SeatViewPhoto> findForAdmin(
            @Param("status") ModerationStatus status,
            @Param("stadium") String stadium,
            @Param("aiLabel") ClassificationLabel aiLabel,
            @Param("adminLabel") ClassificationLabel adminLabel,
            @Param("ticketVerified") Boolean ticketVerified,
            @Param("pendingStatus") ModerationStatus pendingStatus,
            Pageable pageable);

    @Query("""
            SELECT sv FROM SeatViewPhoto sv
            LEFT JOIN FETCH sv.diary d
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
