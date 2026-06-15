package com.example.mate.service;

import com.example.mate.dto.MateSearchTermDTO;
import com.example.mate.entity.MateSearchTerm;
import com.example.mate.repository.MateSearchTermRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MateSearchTermService tests")
class MateSearchTermServiceTest {

    @Mock
    private MateSearchTermRepository mateSearchTermRepository;

    private MateSearchTermService mateSearchTermService;

    @BeforeEach
    void setUp() {
        mateSearchTermService = new MateSearchTermService(mateSearchTermRepository);
    }

    @Test
    @DisplayName("invalid terms are ignored as no-op")
    void recordSearchTerm_invalidTermsAreNoop() {
        mateSearchTermService.recordSearchTerm(" ");
        mateSearchTermService.recordSearchTerm("a");
        mateSearchTermService.recordSearchTerm("123456789012345678901234567890123456789012345678901");

        verifyNoInteractions(mateSearchTermRepository);
    }

    @Test
    @DisplayName("new valid term is normalized and inserted")
    void recordSearchTerm_newTermInsertsNormalizedRow() {
        when(mateSearchTermRepository.incrementDailyTerm(any(), anyString(), anyString(), any()))
                .thenReturn(0);

        mateSearchTermService.recordSearchTerm("  KIA\u0000  응원석  ");

        ArgumentCaptor<MateSearchTerm> captor = ArgumentCaptor.forClass(MateSearchTerm.class);
        verify(mateSearchTermRepository).saveAndFlush(captor.capture());
        MateSearchTerm saved = captor.getValue();
        assertThat(saved.getSearchDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getDisplayTerm()).isEqualTo("KIA 응원석");
        assertThat(saved.getNormalizedTerm()).isEqualTo("kia 응원석");
        assertThat(saved.getSearchCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("existing term is incremented without inserting")
    void recordSearchTerm_existingTermIncrementsOnly() {
        when(mateSearchTermRepository.incrementDailyTerm(any(), anyString(), anyString(), any()))
                .thenReturn(1);

        mateSearchTermService.recordSearchTerm("잠실 블루존");

        verify(mateSearchTermRepository).incrementDailyTerm(
                eq(LocalDate.now()),
                eq("잠실 블루존"),
                eq("잠실 블루존"),
                any());
        verify(mateSearchTermRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("popular terms aggregate the last seven days and use latest display text")
    void getPopularTerms_aggregatesRecentSevenDays() {
        LocalDate today = LocalDate.now();
        Instant now = Instant.parse("2026-06-09T12:00:00Z");
        when(mateSearchTermRepository.findBySearchDateGreaterThanEqual(today.minusDays(6)))
                .thenReturn(List.of(
                        term(today, "kia", "KIA", 2, now.minusSeconds(60)),
                        term(today.minusDays(2), "kia", "kia", 3, now),
                        term(today.minusDays(6), "잠실", "잠실", 4, now.minusSeconds(10))));

        List<MateSearchTermDTO.PopularResponse> popularTerms = mateSearchTermService.getPopularTerms(5);

        assertThat(popularTerms).extracting(MateSearchTermDTO.PopularResponse::getTerm)
                .containsExactly("kia", "잠실");
        assertThat(popularTerms).extracting(MateSearchTermDTO.PopularResponse::getCount)
                .containsExactly(5L, 4L);
        assertThat(popularTerms).extracting(MateSearchTermDTO.PopularResponse::getRank)
                .containsExactly(1, 2);
        verify(mateSearchTermRepository).findBySearchDateGreaterThanEqual(today.minusDays(6));
    }

    @Test
    @DisplayName("popular term limit is capped")
    void getPopularTerms_capsLimit() {
        LocalDate today = LocalDate.now();
        List<MateSearchTerm> terms = new ArrayList<>();
        for (int i = 0; i < 12; i += 1) {
            terms.add(term(today, "term-" + i, "term-" + i, i + 1, Instant.parse("2026-06-09T12:00:00Z")));
        }
        when(mateSearchTermRepository.findBySearchDateGreaterThanEqual(today.minusDays(6)))
                .thenReturn(terms);

        List<MateSearchTermDTO.PopularResponse> popularTerms = mateSearchTermService.getPopularTerms(50);

        assertThat(popularTerms).hasSize(10);
        assertThat(popularTerms.get(0).getTerm()).isEqualTo("term-11");
    }

    private MateSearchTerm term(
            LocalDate searchDate,
            String normalizedTerm,
            String displayTerm,
            long searchCount,
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
