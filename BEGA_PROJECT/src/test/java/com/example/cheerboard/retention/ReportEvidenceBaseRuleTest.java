package com.example.cheerboard.retention;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ReportEvidenceBaseRuleTest {

    private static final LocalDateTime FIRST = LocalDateTime.of(2026, 1, 10, 9, 0);
    private static final LocalDateTime CYCLE_START = FIRST.plusDays(100);

    @Test
    void noFirstRequestMeansNoBase() {
        assertThat(ReportEvidenceRetentionPolicy.evidenceBase(null, CYCLE_START, CYCLE_START.plusDays(1))).isNull();
    }

    @Test
    void withoutARerequestTheFirstRequestIsTheBase() {
        assertThat(ReportEvidenceRetentionPolicy.evidenceBase(FIRST, FIRST, FIRST.plusDays(5))).isEqualTo(FIRST);
        assertThat(ReportEvidenceRetentionPolicy.evidenceBase(FIRST, null, null)).isEqualTo(FIRST);
    }

    @Test
    void evidenceThatPredatesTheCurrentCycleKeepsTheFirstRequestBase() {
        assertThat(ReportEvidenceRetentionPolicy.evidenceBase(FIRST, CYCLE_START, FIRST.plusDays(10)))
                .isEqualTo(FIRST);
    }

    @Test
    void evidenceFromTheCurrentCycleGetsTheCycleStartAsBase() {
        assertThat(ReportEvidenceRetentionPolicy.evidenceBase(FIRST, CYCLE_START, CYCLE_START.plusDays(1)))
                .isEqualTo(CYCLE_START);
    }

    @Test
    void reportAtTheCycleStartBelongsToTheCurrentCycle() {
        assertThat(ReportEvidenceRetentionPolicy.evidenceBase(FIRST, CYCLE_START, CYCLE_START))
                .isEqualTo(CYCLE_START);
    }
}
