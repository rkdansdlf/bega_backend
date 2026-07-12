package com.example.mate.service;

import com.example.common.config.CacheConfig;
import com.example.mate.dto.MateSearchTermDTO;
import com.example.mate.repository.MateSearchTermLatestDisplayProjection;
import com.example.mate.repository.MateSearchTermPopularProjection;
import com.example.mate.repository.MateSearchTermRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(classes = {
        MateSearchTermService.class,
        MateSearchTermServiceCacheTest.CacheTestConfig.class
})
@DisplayName("MateSearchTermService cache tests")
class MateSearchTermServiceCacheTest {

    @jakarta.annotation.Resource
    private MateSearchTermService mateSearchTermService;

    @jakarta.annotation.Resource
    private MateSearchTermRepository mateSearchTermRepository;

    @jakarta.annotation.Resource
    private CacheManager cacheManager;

    @BeforeEach
    void resetRepositoryAndCache() {
        reset(mateSearchTermRepository);
        Cache cache = cacheManager.getCache(CacheConfig.MATE_POPULAR_SEARCH_TERMS);
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    @DisplayName("popular terms are cached by normalized limit")
    void getPopularTerms_cachesByNormalizedLimit() {
        when(mateSearchTermRepository.findPopularTermSummaries(any(LocalDate.class), any(Pageable.class)))
                .thenReturn(List.of(popular("kia", 7L)));
        when(mateSearchTermRepository.findLatestDisplayTerms(any(LocalDate.class), any()))
                .thenReturn(List.of(display("kia", "KIA")));

        List<MateSearchTermDTO.PopularResponse> first = mateSearchTermService.getPopularTerms(50);
        List<MateSearchTermDTO.PopularResponse> second = mateSearchTermService.getPopularTerms(10);

        assertThat(first).hasSize(1);
        assertThat(second).containsExactlyElementsOf(first);
        verify(mateSearchTermRepository, times(1))
                .findPopularTermSummaries(any(LocalDate.class), any(Pageable.class));
    }

    @Test
    @DisplayName("recording a search term evicts cached popular terms")
    void recordSearchTerm_evictsPopularTermsCache() {
        when(mateSearchTermRepository.findPopularTermSummaries(any(LocalDate.class), any(Pageable.class)))
                .thenReturn(
                        List.of(popular("kia", 7L)),
                        List.of(popular("잠실", 8L)));
        when(mateSearchTermRepository.findLatestDisplayTerms(any(LocalDate.class), any()))
                .thenReturn(
                        List.of(display("kia", "KIA")),
                        List.of(display("잠실", "잠실")));
        when(mateSearchTermRepository.incrementDailyTerm(
                any(LocalDate.class),
                anyString(),
                anyString(),
                any(Instant.class))).thenReturn(1);

        List<MateSearchTermDTO.PopularResponse> before = mateSearchTermService.getPopularTerms(5);
        mateSearchTermService.getPopularTerms(5);
        mateSearchTermService.recordSearchTerm("잠실 블루존");
        List<MateSearchTermDTO.PopularResponse> after = mateSearchTermService.getPopularTerms(5);

        assertThat(before).extracting(MateSearchTermDTO.PopularResponse::getTerm)
                .containsExactly("KIA");
        assertThat(after).extracting(MateSearchTermDTO.PopularResponse::getTerm)
                .containsExactly("잠실");
        verify(mateSearchTermRepository, times(2))
                .findPopularTermSummaries(any(LocalDate.class), any(Pageable.class));
    }

    @Test
    @DisplayName("invalid search terms do not evict cached popular terms")
    void recordSearchTerm_invalidTermKeepsPopularTermsCache() {
        when(mateSearchTermRepository.findPopularTermSummaries(any(LocalDate.class), any(Pageable.class)))
                .thenReturn(List.of(popular("kia", 7L)));
        when(mateSearchTermRepository.findLatestDisplayTerms(any(LocalDate.class), any()))
                .thenReturn(List.of(display("kia", "KIA")));

        List<MateSearchTermDTO.PopularResponse> before = mateSearchTermService.getPopularTerms(5);
        mateSearchTermService.recordSearchTerm(" ");
        List<MateSearchTermDTO.PopularResponse> after = mateSearchTermService.getPopularTerms(5);

        assertThat(after).containsExactlyElementsOf(before);
        verify(mateSearchTermRepository, times(1))
                .findPopularTermSummaries(any(LocalDate.class), any(Pageable.class));
    }

    private MateSearchTermPopularProjection popular(String normalizedTerm, long searchCount) {
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
                return Instant.parse("2026-06-09T12:00:00Z");
            }
        };
    }

    private MateSearchTermLatestDisplayProjection display(String normalizedTerm, String displayTerm) {
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
                return Instant.parse("2026-06-09T12:00:00Z");
            }

            @Override
            public Long getId() {
                return 1L;
            }
        };
    }

    @Configuration
    @EnableCaching
    static class CacheTestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheConfig.MATE_POPULAR_SEARCH_TERMS);
        }

        @Bean
        MateSearchTermRepository mateSearchTermRepository() {
            return mock(MateSearchTermRepository.class);
        }
    }
}
