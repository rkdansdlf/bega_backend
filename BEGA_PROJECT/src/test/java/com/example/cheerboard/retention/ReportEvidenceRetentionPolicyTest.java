package com.example.cheerboard.retention;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ReportEvidenceRetentionPolicyTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 1, 10, 9, 0);

    @Test
    void defaultRetentionIsThirtyDaysAndHardLimitIsNinetyDaysFromTheFirstRequest() {
        assertThat(ReportEvidenceRetentionPolicy.defaultUntil(BASE)).isEqualTo(BASE.plusDays(30));
        assertThat(ReportEvidenceRetentionPolicy.hardLimit(BASE)).isEqualTo(BASE.plusDays(90));
    }

    @Test
    void effectiveUntilUsesDefaultWithoutExtension() {
        LocalDateTime retain = BASE.plusDays(30);
        LocalDateTime hard = BASE.plusDays(90);

        assertThat(ReportEvidenceRetentionPolicy.effectiveUntil(retain, hard, null)).isEqualTo(retain);
    }

    @Test
    void effectiveUntilIgnoresAnExtensionThatDoesNotPassTheDefault() {
        LocalDateTime retain = BASE.plusDays(30);
        LocalDateTime hard = BASE.plusDays(90);

        assertThat(ReportEvidenceRetentionPolicy.effectiveUntil(retain, hard, BASE.plusDays(20))).isEqualTo(retain);
    }

    @Test
    void effectiveUntilAcceptsAnExtensionWithinTheHardLimit() {
        LocalDateTime retain = BASE.plusDays(30);
        LocalDateTime hard = BASE.plusDays(90);

        assertThat(ReportEvidenceRetentionPolicy.effectiveUntil(retain, hard, BASE.plusDays(60)))
                .isEqualTo(BASE.plusDays(60));
    }

    @Test
    void effectiveUntilNeverExceedsTheHardLimit() {
        LocalDateTime retain = BASE.plusDays(30);
        LocalDateTime hard = BASE.plusDays(90);

        assertThat(ReportEvidenceRetentionPolicy.effectiveUntil(retain, hard, BASE.plusDays(365)))
                .isEqualTo(hard);
    }

    @Test
    void retryDelayDoublesFromTenMinutesAndCapsAtOneDay() {
        LocalDateTime now = BASE;

        assertThat(ReportEvidenceRetentionPolicy.nextAttemptAfter(1, now)).isEqualTo(now.plusMinutes(10));
        assertThat(ReportEvidenceRetentionPolicy.nextAttemptAfter(2, now)).isEqualTo(now.plusMinutes(20));
        assertThat(ReportEvidenceRetentionPolicy.nextAttemptAfter(5, now)).isEqualTo(now.plusMinutes(160));
        assertThat(ReportEvidenceRetentionPolicy.nextAttemptAfter(50, now)).isEqualTo(now.plusHours(24));
    }
}
