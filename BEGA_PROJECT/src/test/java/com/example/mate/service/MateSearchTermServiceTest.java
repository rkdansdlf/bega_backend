package com.example.mate.service;

import com.example.mate.dto.MateSearchTermDTO;
import com.example.mate.entity.MateSearchTerm;
import com.example.mate.repository.MateSearchTermLatestDisplayProjection;
import com.example.mate.repository.MateSearchTermPopularProjection;
import com.example.mate.repository.MateSearchTermRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;

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

    @Mock
    private CacheManager cacheManager;

    private MateSearchTermService mateSearchTermService;

    @BeforeEach
    void setUp() {
        mateSearchTermService = new MateSearchTermService(mateSearchTermRepository, cacheManager);
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
        when(mateSearchTermRepository.findPopularTermSummaries(today.minusDays(6), PageRequest.of(0, 5)))
                .thenReturn(List.of(
                        popular("kia", 5, now),
                        popular("잠실", 4, now.minusSeconds(10))));
        when(mateSearchTermRepository.findLatestDisplayTerms(today.minusDays(6), List.of("kia", "잠실")))
                .thenReturn(List.of(
                        display("kia", "kia", now, 2L),
                        display("잠실", "잠실", now.minusSeconds(10), 3L)));

        List<MateSearchTermDTO.PopularResponse> popularTerms = mateSearchTermService.getPopularTerms(5);

        assertThat(popularTerms).extracting(MateSearchTermDTO.PopularResponse::getTerm)
                .containsExactly("kia", "잠실");
        assertThat(popularTerms).extracting(MateSearchTermDTO.PopularResponse::getCount)
                .containsExactly(5L, 4L);
        assertThat(popularTerms).extracting(MateSearchTermDTO.PopularResponse::getRank)
                .containsExactly(1, 2);
        verify(mateSearchTermRepository).findPopularTermSummaries(today.minusDays(6), PageRequest.of(0, 5));
    }

    @Test
    @DisplayName("popular term limit is capped")
    void getPopularTerms_capsLimit() {
        LocalDate today = LocalDate.now();
        Instant now = Instant.parse("2026-06-09T12:00:00Z");
        List<MateSearchTermPopularProjection> summaries = new ArrayList<>();
        List<MateSearchTermLatestDisplayProjection> displayTerms = new ArrayList<>();
        for (int i = 0; i < 12; i += 1) {
            String normalizedTerm = "term-" + (11 - i);
            summaries.add(popular(normalizedTerm, 12 - i, now.minusSeconds(i)));
            displayTerms.add(display(normalizedTerm, normalizedTerm, now.minusSeconds(i), (long) i));
        }
        when(mateSearchTermRepository.findPopularTermSummaries(today.minusDays(6), PageRequest.of(0, 10)))
                .thenReturn(summaries.subList(0, 10));
        when(mateSearchTermRepository.findLatestDisplayTerms(
                today.minusDays(6),
                summaries.subList(0, 10).stream()
                        .map(MateSearchTermPopularProjection::getNormalizedTerm)
                        .toList()))
                .thenReturn(displayTerms.subList(0, 10));

        List<MateSearchTermDTO.PopularResponse> popularTerms = mateSearchTermService.getPopularTerms(50);

        assertThat(popularTerms).hasSize(10);
        assertThat(popularTerms.get(0).getTerm()).isEqualTo("term-11");
    }

    private MateSearchTermPopularProjection popular(
            String normalizedTerm,
            long searchCount,
            Instant lastSearchedAt) {
        return new MateSearchTermPopularProjection() {
            @Override
            public String getNormalizedTerm() {
                return normalizedTerm;
            }

            @Override
            public Long getSearchCount() {
                return searchCount;
            }

            @Override
            public Instant getLastSearchedAt() {
                return lastSearchedAt;
            }
        };
    }

    private MateSearchTermLatestDisplayProjection display(
            String normalizedTerm,
            String displayTerm,
            Instant lastSearchedAt,
            Long id) {
        return new MateSearchTermLatestDisplayProjection() {
            @Override
            public String getNormalizedTerm() {
                return normalizedTerm;
            }

            @Override
            public String getDisplayTerm() {
                return displayTerm;
            }

            @Override
            public Instant getLastSearchedAt() {
                return lastSearchedAt;
            }

            @Override
            public Long getId() {
                return id;
            }
        };
    }
}
