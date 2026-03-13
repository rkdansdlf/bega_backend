package com.example.homepage;

import com.example.kbo.entity.PlayerMovement;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OffseasonServiceAmountParsingTest {

    private final OffseasonService offseasonService = new OffseasonService(null);

    @Test
    void parseAmountShouldSupportCommaSeparatedEok() {
        Long parsedAmount = ReflectionTestUtils.invokeMethod(
                offseasonService,
                "parseAmount",
                "FA 계약 4년 총액 1,200억"
        );

        assertEquals(1200L, parsedAmount);
    }

    @Test
    void extractDisplayAmountShouldKeepFullMatchedExpression() {
        String displayAmount = ReflectionTestUtils.invokeMethod(
                offseasonService,
                "extractDisplayAmount",
                "외국인 선수 재계약 1년 80만 달러, 옵션 별도"
        );

        assertEquals("1년 80만 달러", displayAmount);
    }

    @Test
    void convertToDtoShouldExposeStructuredDetailsAndPreferStructuredContractValue() {
        PlayerMovement movement = PlayerMovement.builder()
                .id(7L)
                .movementDate(LocalDate.of(2025, 1, 15))
                .section("FA")
                .teamCode("LG")
                .playerName("홍길동")
                .details("원소속팀 잔류")
                .contractTerm("4년")
                .contractValue("4년 80억")
                .optionDetails("옵션 5억 포함")
                .counterpartyTeam("SSG")
                .counterpartyDetails("보상선수 없음")
                .sourceLabel("구단 발표")
                .sourceUrl("https://example.com/move/7")
                .announcedAt(LocalDateTime.of(2025, 1, 15, 9, 30))
                .build();

        OffseasonMovementDto dto = ReflectionTestUtils.invokeMethod(
                offseasonService,
                "convertToDto",
                movement
        );

        assertEquals("원소속팀 잔류", dto.getSummary());
        assertEquals("4년", dto.getContractTerm());
        assertEquals("4년 80억", dto.getContractValue());
        assertEquals("옵션 5억 포함", dto.getOptionDetails());
        assertEquals("SSG", dto.getCounterpartyTeam());
        assertEquals("보상선수 없음", dto.getCounterpartyDetails());
        assertEquals("구단 발표", dto.getSourceLabel());
        assertEquals("https://example.com/move/7", dto.getSourceUrl());
        assertEquals("2025-01-15T09:30", dto.getAnnouncedAt());
        assertEquals(80L, dto.getEstimatedAmount());
        assertEquals("4년 80억", dto.getDisplayAmount());
    }

    @Test
    void convertToDtoShouldFallbackToDetailsWhenStructuredSummaryIsMissing() {
        PlayerMovement movement = PlayerMovement.builder()
                .id(8L)
                .movementDate(LocalDate.of(2025, 2, 1))
                .section("외국인")
                .teamCode("NC")
                .playerName("존 도우")
                .summary("   ")
                .details("재계약 완료")
                .build();

        OffseasonMovementDto dto = ReflectionTestUtils.invokeMethod(
                offseasonService,
                "convertToDto",
                movement
        );

        assertEquals("재계약 완료", dto.getSummary());
        assertEquals("재계약 완료", dto.getRemarks());
        assertNull(dto.getContractValue());
    }
}
