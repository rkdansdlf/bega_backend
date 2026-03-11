package com.example.homepage;

import com.example.kbo.entity.PlayerMovement;
import com.example.kbo.repository.PlayerMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OffseasonService {

    private final PlayerMovementRepository repository;

    // Pattern to catch sortable amount tokens like "60억", "100억", "4년 60억"
    private static final Pattern MONEY_PATTERN = Pattern.compile("(\\d{1,4}(?:,\\d{3})*)\\s*억");
    private static final Pattern DISPLAY_AMOUNT_PATTERN = Pattern.compile("(?:\\d+\\s*년\\s*)?\\d{1,4}(?:,\\d{3})*\\s*(?:억|만\\s*원|만\\s*달러|달러)");

    public List<OffseasonMovementDto> getOffseasonMovements() {
        // Filter for 2025 Stove League (Movements after 2024-11-01)
        java.time.LocalDate stoveLeagueStart = java.time.LocalDate.of(2024, 11, 1);
        List<PlayerMovement> movements = repository
                .findByMovementDateGreaterThanEqualOrderByMovementDateDesc(stoveLeagueStart);

        return movements.stream()
                .map(this::convertToDto)
                .sorted(movementComparator()) // Sort: Big Events first (by amount), then Date
                .collect(Collectors.toList());
    }

    private OffseasonMovementDto convertToDto(PlayerMovement entity) {
        String remarks = trimToEmpty(entity.getDetails());
        String summary = firstNonBlank(entity.getSummary(), remarks);
        String contractValue = trimToNull(entity.getContractValue());
        Long amount = parseAmount(firstNonBlank(contractValue, remarks));
        boolean isBig = isBigEvent(entity.getSection(), amount);

        return OffseasonMovementDto.builder()
                .id(entity.getId())
                .date(entity.getMovementDate().toString())
                .section(entity.getSection())
                .team(entity.getTeamCode())
                .player(entity.getPlayerName())
                .summary(summary)
                .remarks(remarks)
                .contractTerm(trimToNull(entity.getContractTerm()))
                .contractValue(contractValue)
                .optionDetails(trimToNull(entity.getOptionDetails()))
                .counterpartyTeam(trimToNull(entity.getCounterpartyTeam()))
                .counterpartyDetails(trimToNull(entity.getCounterpartyDetails()))
                .sourceLabel(trimToNull(entity.getSourceLabel()))
                .sourceUrl(trimToNull(entity.getSourceUrl()))
                .announcedAt(entity.getAnnouncedAt() != null ? entity.getAnnouncedAt().toString() : null)
                .isBigEvent(isBig)
                .estimatedAmount(amount)
                .displayAmount(extractDisplayAmount(firstNonBlank(contractValue, remarks)))
                .build();
    }

    private Long parseAmount(String text) {
        if (!StringUtils.hasText(text)) {
            return 0L;
        }

        Matcher matcher = MONEY_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                // Return amount in "Eok" (100 million) units
                return Long.parseLong(matcher.group(1).replace(",", ""));
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }

    private String extractDisplayAmount(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        Matcher matcher = DISPLAY_AMOUNT_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group().replaceAll("\\s+", " ").trim();
        }
        return null;
    }

    private boolean isBigEvent(String section, Long amount) {
        // Condition 1: High value (e.g., >= 20 Eok)
        if (amount >= 20)
            return true;

        if (section == null)
            return false;

        // Normalize section string
        String normalizedSection = section.trim();

        // Condition 2: Important Sections (FA, Trade with money typically)
        if (normalizedSection.contains("FA") || normalizedSection.contains("트레이드")) {
            // Treat all FA/Trade as big events, even if amount parsing fails
            return true;
        }

        return false;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }

        return null;
    }

    private Comparator<OffseasonMovementDto> movementComparator() {
        return (o1, o2) -> {
            // Priority 1: Big Event vs Normal
            if (o1.isBigEvent() && !o2.isBigEvent())
                return -1;
            if (!o1.isBigEvent() && o2.isBigEvent())
                return 1;

            // Priority 2: If both Big, sort by Amount DESC
            if (o1.isBigEvent() && o2.isBigEvent()) {
                return Long.compare(o2.getEstimatedAmount(), o1.getEstimatedAmount());
            }

            // Priority 3: Default Date Desc
            return o2.getDate().compareTo(o1.getDate());
        };
    }

    /**
     * Returns offseason metadata (awards, postseason results) for a given year.
     */
    public OffseasonMetaDto getOffseasonMetadata(int year) {
        AwardDescriptionGenerator generator = new AwardDescriptionGenerator();

        // Simulating DB fetch: List<AwardEntity> awardEntities =
        // awardRepository.findByYear(year);
        // We create them manually here to demonstrate the generator logic
        List<com.example.kbo.entity.AwardEntity> awardEntities = java.util.List.of(
                com.example.kbo.entity.AwardEntity.builder().awardType("MVP").playerName("코디 폰세").team("한화").year(2025)
                        .build(),
                com.example.kbo.entity.AwardEntity.builder().awardType("신인상").playerName("안현민").team("KT").year(2025)
                        .build(),
                com.example.kbo.entity.AwardEntity.builder().awardType("홈런상").playerName("르윈 디아즈").team("삼성")
                        .year(2025).build(),
                com.example.kbo.entity.AwardEntity.builder().awardType("홀드상").playerName("노경은").team("SSG").year(2025)
                        .build(),
                com.example.kbo.entity.AwardEntity.builder().awardType("도루상").playerName("박해민").team("LG").year(2025)
                        .build(),
                com.example.kbo.entity.AwardEntity.builder().awardType("타율상").playerName("양의지").team("두산").year(2025)
                        .build());

        List<OffseasonMetaDto.AwardDto> awards = awardEntities.stream()
                .map(entity -> OffseasonMetaDto.AwardDto.builder()
                        .award(entity.getAwardType())
                        .playerName(entity.getPlayerName())
                        .team(entity.getTeam())
                        .stats(generator.generateDescription(entity)) // Dynamic Generation!
                        .build())
                .collect(java.util.stream.Collectors.toList());

        // 2025 PostSeason Results (Still Static for now)
        List<OffseasonMetaDto.PostSeasonResultDto> postSeasonResults = List.of(
                OffseasonMetaDto.PostSeasonResultDto.builder()
                        .title("한국시리즈 우승").result("LG 트윈스").detail("한화 이글스 KS 4-1 승").build(),
                OffseasonMetaDto.PostSeasonResultDto.builder()
                        .title("플레이오프").result("한화 이글스").detail("삼성 라이온즈 PO 3-2 승").build(),
                OffseasonMetaDto.PostSeasonResultDto.builder()
                        .title("준플레이오프").result("삼성 라이온즈").detail("SSG 랜더스 준PO 2-3 승").build(),
                OffseasonMetaDto.PostSeasonResultDto.builder()
                        .title("와일드카드").result("삼성 라이온즈").detail("NC 다이노스 WC 1-1 승").build());

        return OffseasonMetaDto.builder()
                .awards(awards)
                .postSeasonResults(postSeasonResults)
                .build();
    }
}
