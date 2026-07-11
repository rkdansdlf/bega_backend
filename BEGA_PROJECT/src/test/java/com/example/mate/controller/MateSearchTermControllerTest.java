package com.example.mate.controller;

import com.example.common.exception.AuthenticationRequiredException;
import com.example.common.ratelimit.RateLimit;
import com.example.mate.dto.MateSearchTermDTO;
import com.example.mate.service.MateSearchTermService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MateSearchTermController tests")
class MateSearchTermControllerTest {

    @Mock
    private MateSearchTermService mateSearchTermService;

    @InjectMocks
    private MateSearchTermController mateSearchTermController;

    @Test
    @DisplayName("record search term returns 204")
    void recordSearchTerm_returnsNoContent() {
        MateSearchTermDTO.RecordRequest request = new MateSearchTermDTO.RecordRequest("잠실 블루존");

        ResponseEntity<Void> response = mateSearchTermController.recordSearchTerm(request, 42L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(mateSearchTermService).recordSearchTerm("잠실 블루존");
    }

    @Test
    @DisplayName("record search term requires authentication")
    void recordSearchTerm_requiresAuthentication() {
        MateSearchTermDTO.RecordRequest request = new MateSearchTermDTO.RecordRequest("잠실 블루존");

        assertThatThrownBy(() -> mateSearchTermController.recordSearchTerm(request, null))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    @DisplayName("popular search terms return ranked responses")
    void getPopularSearchTerms_returnsResponses() {
        MateSearchTermDTO.PopularResponse popular = MateSearchTermDTO.PopularResponse.builder()
                .term("KIA")
                .count(3L)
                .rank(1)
                .build();
        when(mateSearchTermService.getPopularTerms(5)).thenReturn(List.of(popular));

        ResponseEntity<List<MateSearchTermDTO.PopularResponse>> response =
                mateSearchTermController.getPopularSearchTerms(5);

        assertThat(response.getBody()).containsExactly(popular);
        assertThat(response.getHeaders().getCacheControl()).contains("max-age=30", "public");
    }

    @Test
    @DisplayName("record endpoint is rate limited")
    void recordSearchTerm_hasRateLimitAnnotation() throws Exception {
        Method method = MateSearchTermController.class.getMethod(
                "recordSearchTerm",
                MateSearchTermDTO.RecordRequest.class,
                Long.class);

        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        assertThat(rateLimit).isNotNull();
        assertThat(rateLimit.limit()).isEqualTo(60);
        assertThat(rateLimit.window()).isEqualTo(60);
        assertThat(rateLimit.key()).isEqualTo("mate:search-terms");
    }
}
