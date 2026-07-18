package com.example.mate.repository;

import com.example.mate.entity.MateSearchTerm;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mate_search_term_projection;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false",
        "logging.level.org.hibernate.SQL=ERROR",
        "logging.level.org.hibernate.orm.jdbc.bind=ERROR"
})
class MateSearchTermRepositoryProjectionIntegrationTest {

    @Autowired
    private MateSearchTermRepository mateSearchTermRepository;

    @Test
    @DisplayName("popular term projections aggregate in the database and keep latest display terms bounded")
    void popularTermProjectionsAggregateInDatabase() {
        LocalDate today = LocalDate.of(2026, 6, 9);
        LocalDate startDate = today.minusDays(6);
        Instant now = Instant.parse("2026-06-09T12:00:00Z");

        mateSearchTermRepository.saveAll(List.of(
                term(today, "kia", "KIA", 2L, now.minusSeconds(60)),
                term(today.minusDays(2), "kia", "kia", 3L, now),
                term(startDate, "잠실", "잠실", 4L, now.minusSeconds(10)),
                term(today, "두산", "두산", 1L, now.minusSeconds(5)),
                term(startDate.minusDays(1), "old", "old", 99L, now)));
        mateSearchTermRepository.flush();

        List<MateSearchTermPopularProjection> summaries =
                mateSearchTermRepository.findPopularTermSummaries(startDate, PageRequest.of(0, 2));

        assertThat(summaries)
                .extracting(
                        MateSearchTermPopularProjection::getNormalizedTerm,
                        MateSearchTermPopularProjection::getSearchCount)
                .containsExactly(
                        tuple("kia", 5L),
                        tuple("잠실", 4L));

        List<String> normalizedTerms = summaries.stream()
                .map(MateSearchTermPopularProjection::getNormalizedTerm)
                .toList();
        List<MateSearchTermLatestDisplayProjection> displayTerms =
                mateSearchTermRepository.findLatestDisplayTerms(startDate, normalizedTerms);

        assertThat(displayTerms)
                .extracting(
                        MateSearchTermLatestDisplayProjection::getNormalizedTerm,
                        MateSearchTermLatestDisplayProjection::getDisplayTerm)
                .contains(
                        tuple("kia", "kia"),
                        tuple("잠실", "잠실"));
    }

    private MateSearchTerm term(
            LocalDate searchDate,
            String normalizedTerm,
            String displayTerm,
            Long searchCount,
            Instant lastSearchedAt) {
        return MateSearchTerm.builder()
                .searchDate(searchDate)
                .normalizedTerm(normalizedTerm)
                .displayTerm(displayTerm)
                .searchCount(searchCount)
                .lastSearchedAt(lastSearchedAt)
                .build();
    }
}
