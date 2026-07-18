package com.example.mate.repository;

import com.example.mate.entity.MateSearchTerm;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface MateSearchTermRepository extends JpaRepository<MateSearchTerm, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE MateSearchTerm term
               SET term.displayTerm = :displayTerm,
                   term.searchCount = term.searchCount + 1,
                   term.lastSearchedAt = :searchedAt,
                   term.updatedAt = :searchedAt
             WHERE term.searchDate = :searchDate
               AND term.normalizedTerm = :normalizedTerm
            """)
    int incrementDailyTerm(
            @Param("searchDate") LocalDate searchDate,
            @Param("normalizedTerm") String normalizedTerm,
            @Param("displayTerm") String displayTerm,
            @Param("searchedAt") Instant searchedAt);

    @Query("""
            SELECT term.normalizedTerm AS normalizedTerm,
                   SUM(term.searchCount) AS searchCount,
                   MAX(term.lastSearchedAt) AS lastSearchedAt
              FROM MateSearchTerm term
             WHERE term.searchDate >= :searchDate
             GROUP BY term.normalizedTerm
             ORDER BY SUM(term.searchCount) DESC,
                      MAX(term.lastSearchedAt) DESC,
                      term.normalizedTerm ASC
            """)
    List<MateSearchTermPopularProjection> findPopularTermSummaries(
            @Param("searchDate") LocalDate searchDate,
            Pageable pageable);

    @Query("""
            SELECT term.normalizedTerm AS normalizedTerm,
                   term.displayTerm AS displayTerm,
                   term.lastSearchedAt AS lastSearchedAt,
                   term.id AS id
              FROM MateSearchTerm term
             WHERE term.searchDate >= :searchDate
               AND term.normalizedTerm IN :normalizedTerms
               AND term.lastSearchedAt = (
                    SELECT MAX(latest.lastSearchedAt)
                      FROM MateSearchTerm latest
                     WHERE latest.searchDate >= :searchDate
                       AND latest.normalizedTerm = term.normalizedTerm
               )
             ORDER BY term.normalizedTerm ASC,
                      term.id DESC
            """)
    List<MateSearchTermLatestDisplayProjection> findLatestDisplayTerms(
            @Param("searchDate") LocalDate searchDate,
            @Param("normalizedTerms") List<String> normalizedTerms);
}
