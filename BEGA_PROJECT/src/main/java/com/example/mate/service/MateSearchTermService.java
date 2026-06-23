package com.example.mate.service;

import com.example.mate.dto.MateSearchTermDTO;
import com.example.mate.entity.MateSearchTerm;
import com.example.mate.repository.MateSearchTermRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
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
        } catch (DataIntegrityViolationException race) {
            log.debug("Mate search term insert raced; retrying as increment term={}", term.normalized());
            mateSearchTermRepository.incrementDailyTerm(
                    searchDate,
                    term.normalized(),
                    term.display(),
                    searchedAt);
        }
    }

    @Transactional(readOnly = true)
    public List<MateSearchTermDTO.PopularResponse> getPopularTerms(Integer requestedLimit) {
        int limit = normalizeLimit(requestedLimit);
        LocalDate startDate = LocalDate.now().minusDays(POPULAR_WINDOW_DAYS - 1L);
        Map<String, SearchTermAggregate> aggregates = new HashMap<>();

        mateSearchTermRepository.findBySearchDateGreaterThanEqual(startDate).forEach(term -> {
            SearchTermAggregate aggregate = aggregates.computeIfAbsent(
                    term.getNormalizedTerm(),
                    ignored -> new SearchTermAggregate(term.getNormalizedTerm()));
            aggregate.add(term);
        });

        List<SearchTermAggregate> rankedAggregates = aggregates.values().stream()
                .sorted(Comparator
                        .comparingLong(SearchTermAggregate::count).reversed()
                        .thenComparing(SearchTermAggregate::lastSearchedAt, Comparator.reverseOrder())
                        .thenComparing(SearchTermAggregate::normalizedTerm))
                .limit(limit)
                .toList();

        return IntStream.range(0, rankedAggregates.size())
                .mapToObj(index -> MateSearchTermDTO.PopularResponse.builder()
                        .term(rankedAggregates.get(index).displayTerm())
                        .count(rankedAggregates.get(index).count())
                        .rank(index + 1)
                        .build())
                .toList();
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

    private static class SearchTermAggregate {
        private final String normalizedTerm;
        private String displayTerm;
        private long count;
        private Instant lastSearchedAt = Instant.EPOCH;

        SearchTermAggregate(String normalizedTerm) {
            this.normalizedTerm = normalizedTerm;
        }

        void add(MateSearchTerm term) {
            count += term.getSearchCount() == null ? 0L : term.getSearchCount();
            Instant candidateLastSearchedAt = term.getLastSearchedAt() == null
                    ? Instant.EPOCH
                    : term.getLastSearchedAt();
            if (displayTerm == null || candidateLastSearchedAt.isAfter(lastSearchedAt)) {
                displayTerm = term.getDisplayTerm();
                lastSearchedAt = candidateLastSearchedAt;
            }
        }

        String normalizedTerm() {
            return normalizedTerm;
        }

        String displayTerm() {
            return displayTerm;
        }

        long count() {
            return count;
        }

        Instant lastSearchedAt() {
            return lastSearchedAt;
        }
    }
}
