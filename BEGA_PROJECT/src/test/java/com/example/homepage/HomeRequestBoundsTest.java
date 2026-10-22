package com.example.homepage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.common.exception.BadRequestBusinessException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class HomeRequestBoundsTest {

    @Test
    void acceptsTheConfiguredDateAndSeasonBoundaries() {
        assertThat(HomeRequestBounds.requireSupportedDate(LocalDate.of(1982, 1, 1)))
                .isEqualTo(LocalDate.of(1982, 1, 1));
        assertThat(HomeRequestBounds.requireSupportedDate(LocalDate.of(2030, 12, 31)))
                .isEqualTo(LocalDate.of(2030, 12, 31));
        HomeRequestBounds.requireSupportedSeasonYear(1982);
        HomeRequestBounds.requireSupportedSeasonYear(2030);
        HomeRequestBounds.requireSupportedSeasonYear(null);
    }

    @Test
    void rejectsValuesImmediatelyOutsideConfiguredBounds() {
        assertThatThrownBy(() -> HomeRequestBounds.requireSupportedDate(LocalDate.of(1981, 12, 31)))
                .isInstanceOf(BadRequestBusinessException.class)
                .hasMessageContaining("1982-01-01");
        assertThatThrownBy(() -> HomeRequestBounds.requireSupportedDate(LocalDate.of(2031, 1, 1)))
                .isInstanceOf(BadRequestBusinessException.class)
                .hasMessageContaining("2030-12-31");
        assertThatThrownBy(() -> HomeRequestBounds.requireSupportedSeasonYear(1981))
                .isInstanceOf(BadRequestBusinessException.class)
                .hasMessageContaining("1982년");
        assertThatThrownBy(() -> HomeRequestBounds.requireSupportedSeasonYear(2031))
                .isInstanceOf(BadRequestBusinessException.class)
                .hasMessageContaining("2030년");
    }
}
