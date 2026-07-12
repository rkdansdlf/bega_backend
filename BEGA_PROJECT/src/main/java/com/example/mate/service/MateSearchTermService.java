package com.example.mate.service;

import com.example.common.config.CacheConfig;
import com.example.mate.dto.MateSearchTermDTO;
import com.example.mate.entity.MateSearchTerm;
import com.example.mate.repository.MateSearchTermLatestDisplayProjection;
import com.example.mate.repository.MateSearchTermPopularProjection;
import com.example.mate.repository.MateSearchTermRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class MateSearchTermService {

    private static final int MIN_TERM_LENGTH = 2;
    private static final int MAX_TERM_LENGTH = 50;
    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 10;
    private static final int POPULAR_WINDOW_DAYS = 7;
    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final MateSearchTermRepository mateSearchTermRepository;
    private final CacheManager cacheManager;

    @Transactional
    public void recordSearchTerm(String rawTerm) {
        Optional<NormalizedSearchTerm> normalizedTerm = normalize(rawTerm);
        if (normalizedTerm.isEmpty()) {
            return;
        }

        NormalizedSearchTerm term = normalizedTerm.get();
        LocalDate searchDate = LocalDate.now();
        Instant searchedAt = Instant.now();
        int updatedRows = mateSearchTermRepository.incrementDailyTerm(
                searchDate,
                term.normalized(),
                term.display(),
                searchedAt);

        if (updatedRows > 0) {
            evictPopularTermsCache();
            return;
        }

        try {
            mateSearchTermRepository.saveAndFlush(MateSearchTerm.builder()
                    .searchDate(searchDate)
                    .normalizedTerm(term.normalized())
                    .displayTerm(term.display())
                    .searchCount(1L)
                    .lastSearchedAt(searchedAt)
                    .build());
            evictPopularTermsCache();
        } catch (DataIntegrityViolationException race) {
            log.debug("Mate search term insert raced; retrying as increment term={}", term.normalized());
            mateSearchTermRepository.incrementDailyTerm(
                    searchDate,
                    term.normalized(),
                    term.display(),
                    searchedAt);
            evictPopularTermsCache();
        }
    }

    @Transactional(readOnly = true)
    @Cacheable(
            value = CacheConfig.MATE_POPULAR_SEARCH_TERMS,
            key = "#root.target.popularTermsCacheKey(#requestedLimit)",
            sync = true)
    public List<MateSearchTermDTO.PopularResponse> getPopularTerms(Integer requestedLimit) {
        int limit = normalizeLimit(requestedLimit);
        LocalDate startDate = LocalDate.now().minusDays(POPULAR_WINDOW_DAYS - 1L);
        List<MateSearchTermPopularProjection> popularTerms =
                mateSearchTermRepository.findPopularTermSummaries(startDate, PageRequest.of(0, limit));
        if (popularTerms.isEmpty()) {
            return List.of();
        }

        Map<String, String> latestDisplayTerms = latestDisplayTerms(startDate, popularTerms);
        return IntStream.range(0, popularTerms.size())
                .mapToObj(index -> MateSearchTermDTO.PopularResponse.builder()
                        .term(displayTerm(popularTerms.get(index), latestDisplayTerms))
                        .count(searchCount(popularTerms.get(index)))
                        .rank(index + 1)
                        .build())
                .toList();
    }

    public int popularTermsCacheKey(Integer requestedLimit) {
        return normalizeLimit(requestedLimit);
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requestedLimit, MAX_LIMIT);
    }

    private Optional<NormalizedSearchTerm> normalize(String rawTerm) {
        if (rawTerm == null) {
            return Optional.empty();
        }

        String display = WHITESPACE.matcher(CONTROL_CHARS.matcher(rawTerm).replaceAll(" "))
                .replaceAll(" ")
                .trim();
        if (display.length() < MIN_TERM_LENGTH || display.length() > MAX_TERM_LENGTH) {
            return Optional.empty();
        }

        String normalized = display.toLowerCase(Locale.ROOT);
        return Optional.of(new NormalizedSearchTerm(display, normalized));
    }

    private record NormalizedSearchTerm(String display, String normalized) {
    }

    private void evictPopularTermsCache() {
        Cache cache = cacheManager.getCache(CacheConfig.MATE_POPULAR_SEARCH_TERMS);
        if (cache != null) {
            cache.clear();
        }
    }

    private Map<String, String> latestDisplayTerms(
            LocalDate startDate,
            List<MateSearchTermPopularProjection> popularTerms) {
        List<String> normalizedTerms = popularTerms.stream()
                .map(MateSearchTermPopularProjection::getNormalizedTerm)
                .toList();
        Map<String, String> displayTerms = new LinkedHashMap<>();
        for (MateSearchTermLatestDisplayProjection latestDisplay :
                mateSearchTermRepository.findLatestDisplayTerms(startDate, normalizedTerms)) {
            displayTerms.putIfAbsent(latestDisplay.getNormalizedTerm(), latestDisplay.getDisplayTerm());
        }
        return displayTerms;
    }

    private String displayTerm(
            MateSearchTermPopularProjection popularTerm,
            Map<String, String> latestDisplayTerms) {
        return Optional.ofNullable(latestDisplayTerms.get(popularTerm.getNormalizedTerm()))
                .orElse(popularTerm.getNormalizedTerm());
    }

    private long searchCount(MateSearchTermPopularProjection popularTerm) {
        return popularTerm.getSearchCount() == null ? 0L : popularTerm.getSearchCount();
    }
}
