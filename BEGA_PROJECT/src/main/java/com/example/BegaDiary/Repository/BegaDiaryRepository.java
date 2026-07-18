package com.example.BegaDiary.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Entity.BegaDiary.DiaryType;
import com.example.BegaDiary.Entity.BegaDiary.DiaryWinning;
import com.example.auth.entity.UserEntity;

public interface BegaDiaryRepository extends JpaRepository<BegaDiary, Long> {

       Optional<BegaDiary> findByDiaryDate(LocalDate diaryDate);

       boolean existsByUserAndDiaryDate(UserEntity user, LocalDate diaryDate);

       @EntityGraph(attributePaths = "game")
       List<BegaDiary> findByUserId(Long id);

       @EntityGraph(attributePaths = "game")
       List<BegaDiary> findByUserIdOrderByDiaryDateDesc(Long userId);

       @Query("""
                     SELECT
                         d.diaryDate AS diaryDate,
                         d.winning AS winning,
                         d.type AS type,
                         d.stadium AS stadium,
                         d.mood AS mood,
                         g.homeTeam AS homeTeam,
                         g.awayTeam AS awayTeam,
                         ft.teamId AS favoriteTeamId
                     FROM BegaDiary d
                     LEFT JOIN d.game g
                     LEFT JOIN d.user u
                     LEFT JOIN u.favoriteTeam ft
                     WHERE d.user.id = :userId
                     ORDER BY d.diaryDate DESC
                     """)
       List<DiaryStatisticsRow> findStatisticsRowsByUserIdOrderByDiaryDateDesc(@Param("userId") Long userId);

       @EntityGraph(attributePaths = {"user", "game", "photoUrls"})
       @Query("SELECT d FROM BegaDiary d WHERE d.id = :id")
       Optional<BegaDiary> findByIdWithOwnerGameAndPhotos(@Param("id") Long id);

       @EntityGraph(attributePaths = {"user", "game"})
       @Query("SELECT d FROM BegaDiary d WHERE d.id = :id AND d.user.id = :userId")
       Optional<BegaDiary> findByIdAndUserIdWithOwnerAndGame(
                     @Param("id") Long id,
                     @Param("userId") Long userId);

       @EntityGraph(attributePaths = {"user", "game"})
       @Query("SELECT d FROM BegaDiary d WHERE d.id IN :ids")
       List<BegaDiary> findAllByIdInWithOwnerAndGame(@Param("ids") Collection<Long> ids);

       @EntityGraph(attributePaths = {"user", "game", "photoUrls"})
       Optional<BegaDiary> findByIdAndUserId(Long id, Long userId);

       @EntityGraph(attributePaths = "user")
       Page<BegaDiary> findAllBy(Pageable pageable);

       // 총 개수
       @Query("SELECT COUNT(d) FROM BegaDiary d WHERE d.user.id = :userId")
       int countByUserId(@Param("userId") Long userId);

       // 승/패/무 개수
       @Query("SELECT COUNT(d) FROM BegaDiary d WHERE d.user.id = :userId AND d.winning = :winning")
       int countByUserIdAndWinning(@Param("userId") Long userId, @Param("winning") DiaryWinning winning);

       // 연간 개수
       @Query("SELECT COUNT(d) FROM BegaDiary d WHERE d.user.id = :userId AND YEAR(d.diaryDate) = :year")
       int countByUserIdAndYear(@Param("userId") Long userId, @Param("year") int year);

       // 연간 승리 개수
       @Query("SELECT COUNT(d) FROM BegaDiary d WHERE d.user.id = :userId AND YEAR(d.diaryDate) = :year AND d.winning = 'WIN'")
       int countYearlyWins(@Param("userId") Long userId, @Param("year") int year);

       // 가장 많이 간 구장 (TOP 1)
       @Query("SELECT d.stadium, COUNT(d) as cnt FROM BegaDiary d " +
                     "WHERE d.user.id = :userId AND d.stadium IS NOT NULL " +
                     "GROUP BY d.stadium ORDER BY cnt DESC")
       List<Object[]> findMostVisitedStadium(@Param("userId") Long userId);

       // 가장 행복했던 달 (좋음/최고 감정)
       @Query("SELECT MONTH(d.diaryDate), COUNT(d) as cnt FROM BegaDiary d " +
                     "WHERE d.user.id = :userId AND (d.mood = 'BEST') " +
                     "GROUP BY MONTH(d.diaryDate) ORDER BY cnt DESC")
       List<Object[]> findHappiestMonth(@Param("userId") Long userId);

       // 첫 직관 날짜
       @Query("SELECT MIN(d.diaryDate) FROM BegaDiary d WHERE d.user.id = :userId")
       LocalDate findFirstDiaryDate(@Param("userId") Long userId);

       @Query("SELECT COUNT(d) FROM BegaDiary d WHERE d.user.id = :userId AND YEAR(d.diaryDate) = :year AND MONTH(d.diaryDate) = :month")
       int countByUserIdAndYearAndMonth(@Param("userId") Long userId, @Param("year") int year,
                     @Param("month") int month);

       // 좌석 시야 사진 조회 (stadium + section 필터)
       @Query("SELECT d FROM BegaDiary d WHERE d.stadium = :stadium AND d.section = :section AND d.type = :type AND d.photoUrls IS NOT EMPTY ORDER BY d.diaryDate DESC")
       List<BegaDiary> findSeatViewPhotos(
                     @Param("stadium") String stadium,
                     @Param("section") String section,
                     @Param("type") DiaryType type,
                     Pageable pageable);

       // 좌석 시야 사진 조회 (stadium만 필터, section 없는 경우 fallback)
       @Query("SELECT d FROM BegaDiary d WHERE d.stadium = :stadium AND d.type = :type AND d.photoUrls IS NOT EMPTY ORDER BY d.diaryDate DESC")
       List<BegaDiary> findSeatViewPhotosByStadium(
                     @Param("stadium") String stadium,
                     @Param("type") DiaryType type,
                     Pageable pageable);

       @Query("""
                     SELECT d FROM BegaDiary d
                     WHERE d.stadium = :stadium
                       AND (:section IS NULL OR :section = '' OR d.section = :section)
                       AND d.type = :type
                       AND d.photoUrls IS NOT EMPTY
                       AND NOT EXISTS (
                           SELECT 1 FROM SeatViewPhoto sv
                           WHERE sv.diary = d
                       )
                     ORDER BY d.diaryDate DESC
                     """)
       List<BegaDiary> findLegacySeatViewPhotos(
                     @Param("stadium") String stadium,
                     @Param("section") String section,
                     @Param("type") DiaryType type,
                     Pageable pageable);

}
