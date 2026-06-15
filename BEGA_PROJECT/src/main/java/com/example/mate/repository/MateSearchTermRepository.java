package com.example.mate.repository;

import com.example.mate.entity.MateSearchTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
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

    List<MateSearchTerm> findBySearchDateGreaterThanEqual(LocalDate searchDate);
}
