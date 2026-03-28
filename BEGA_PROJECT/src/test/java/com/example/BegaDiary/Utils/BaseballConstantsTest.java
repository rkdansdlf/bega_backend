package com.example.BegaDiary.Utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseballConstantsTest {

    @Test
    void getFullStadiumNameReturnsNullForNullInput() {
        assertThat(BaseballConstants.getFullStadiumName(null)).isNull();
    }

    @Test
    void getTeamKoreanNameReturnsNullForNullInput() {
        assertThat(BaseballConstants.getTeamKoreanName(null)).isNull();
    }
}
