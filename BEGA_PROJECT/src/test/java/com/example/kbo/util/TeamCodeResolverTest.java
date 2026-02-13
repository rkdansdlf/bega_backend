package com.example.kbo.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TeamCodeResolverTest {

    @Test
    @DisplayName("Legacy code를 canonical code로 변환한다")
    void resolveCanonical_legacy() {
        assertThat(TeamCodeResolver.resolveCanonical("SK")).isEqualTo("SSG");
        assertThat(TeamCodeResolver.resolveCanonical("OB")).isEqualTo("DB");
        assertThat(TeamCodeResolver.resolveCanonical("WO")).isEqualTo("KH");
    }

    @Test
    @DisplayName("한글 팀명을 canonical code로 변환한다")
    void resolveCanonical_koreanAlias() {
        assertThat(TeamCodeResolver.resolveCanonical("두산")).isEqualTo("DB");
        assertThat(TeamCodeResolver.resolveCanonical("기아 타이거즈")).isEqualTo("KIA");
    }

    @Test
    @DisplayName("canonical/legacy variants를 반환한다")
    void resolveVariants_containsLegacy() {
        assertThat(TeamCodeResolver.resolveVariants("SSG")).contains("SSG", "SK");
        assertThat(TeamCodeResolver.resolveVariants("KH")).contains("KH", "WO", "NX");
    }

    @Test
    @DisplayName("표시용 팀명을 canonical 기준으로 반환한다")
    void getDisplayName_returnsCanonicalName() {
        assertThat(TeamCodeResolver.getDisplayName("OB")).isEqualTo("OB 베어스");
        assertThat(TeamCodeResolver.getDisplayName("SK")).isEqualTo("SK 와이번스");
    }

    @Test
    @DisplayName("연도 기반 브랜드 코드를 복원한다")
    void resolveCanonical_yearAwareBrand() {
        assertThat(TeamCodeResolver.resolveCanonical("SSG", 2019)).isEqualTo("SK");
        assertThat(TeamCodeResolver.resolveCanonical("KH", 2014)).isEqualTo("NX");
        assertThat(TeamCodeResolver.resolveCanonical("KIA", 2000)).isEqualTo("HT");
    }

    @Test
    @DisplayName("canonical window 내부는 단일코드, 외부는 dual fallback을 적용한다")
    void resolveQueryVariants_windowPolicy() {
        String previousReadMode = System.getProperty("team.code.read.mode");
        String previousStart = System.getProperty("team.code.canonical.window.start");
        String previousEnd = System.getProperty("team.code.canonical.window.end");
        String previousOutside = System.getProperty("team.code.outside.window.mode");

        try {
            System.setProperty("team.code.read.mode", "canonical_only");
            System.setProperty("team.code.canonical.window.start", "2021");
            System.setProperty("team.code.canonical.window.end", "2025");
            System.setProperty("team.code.outside.window.mode", "dual");

            assertThat(TeamCodeResolver.resolveQueryVariants("SSG", 2024)).containsExactly("SSG");
            assertThat(TeamCodeResolver.resolveQueryVariants("SSG", 2019)).contains("SK", "SSG");
        } finally {
            restoreSystemProperty("team.code.read.mode", previousReadMode);
            restoreSystemProperty("team.code.canonical.window.start", previousStart);
            restoreSystemProperty("team.code.canonical.window.end", previousEnd);
            restoreSystemProperty("team.code.outside.window.mode", previousOutside);
        }
    }

    @Test
    @DisplayName("특수 코드 입력은 보존하고 치환하지 않는다")
    void specialCodes_arePreserved() {
        assertThat(TeamCodeResolver.resolveCanonical("EA")).isEqualTo("EA");
        assertThat(TeamCodeResolver.resolveCanonical("WE")).isEqualTo("WE");
        assertThat(TeamCodeResolver.resolveQueryVariants("EA", 2024)).containsExactly("EA");
    }

    private static void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }
}
