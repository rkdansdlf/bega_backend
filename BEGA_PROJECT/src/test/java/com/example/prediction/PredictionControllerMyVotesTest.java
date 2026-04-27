package com.example.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.common.exception.GlobalExceptionHandler;

class PredictionControllerMyVotesTest {

    @Test
    void nonLoginRequestShouldReturnUnauthorizedWithEmptyVotes() throws Exception {
        PredictionService predictionService = mock(PredictionService.class);
        PredictionRepository predictionRepository = mock(PredictionRepository.class);
        PredictionController controller =
                new PredictionController(predictionService, predictionRepository);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/predictions/my-votes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gameIds": ["GAME-1", "GAME-2"]
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.data.votes").isMap());

        verifyNoInteractions(predictionService, predictionRepository);
    }

    @Test
    void shouldReturnEmptyVotesOnLoginWithEmptyRequest() {
        PredictionService predictionService = mock(PredictionService.class);
        PredictionRepository predictionRepository = mock(PredictionRepository.class);
        PredictionController controller =
                new PredictionController(predictionService, predictionRepository);

        Principal principal = () -> "1";
        PredictionMyVotesRequestDto request = new PredictionMyVotesRequestDto();
        request.setGameIds(List.of());

        ResponseEntity<?> response = controller.getMyVotesBulk(request, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(Map.of("votes", Map.of()));
        verifyNoInteractions(predictionRepository);
    }

    @Test
    void nullGameIdsShouldReturnBadRequest() throws Exception {
        PredictionService predictionService = mock(PredictionService.class);
        PredictionRepository predictionRepository = mock(PredictionRepository.class);
        PredictionController controller = new PredictionController(predictionService, predictionRepository);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/predictions/my-votes")
                        .principal(() -> "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gameIds": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(predictionRepository);
    }

    @Test
    void malformedGameIdsShouldReturnBadRequest() throws Exception {
        PredictionService predictionService = mock(PredictionService.class);
        PredictionRepository predictionRepository = mock(PredictionRepository.class);
        PredictionController controller = new PredictionController(predictionService, predictionRepository);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/predictions/my-votes")
                        .principal(() -> "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gameIds": ["GAME-1", "BAD ID!"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(predictionRepository);
    }

    @Test
    void shouldReturnNullForMissingGameIdsOnly() {
        PredictionService predictionService = mock(PredictionService.class);
        PredictionRepository predictionRepository = mock(PredictionRepository.class);
        PredictionController controller =
                new PredictionController(predictionService, predictionRepository);

        Principal principal = () -> "1";
        PredictionMyVotesRequestDto request = new PredictionMyVotesRequestDto();
        request.setGameIds(List.of("GAME-10", "GAME-11"));

        ResponseEntity<?> response = controller.getMyVotesBulk(request, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, String> votes = extractVotes(response);
        assertThat(votes).hasSize(2);
        assertThat(votes).containsEntry("GAME-10", null);
        assertThat(votes).containsEntry("GAME-11", null);
    }

    @Test
    void shouldDeduplicateDuplicateGameIdsInResponseKeys() {
        PredictionService predictionService = mock(PredictionService.class);
        PredictionRepository predictionRepository = mock(PredictionRepository.class);
        PredictionController controller =
                new PredictionController(predictionService, predictionRepository);

        Principal principal = () -> "1";
        PredictionMyVotesRequestDto request = new PredictionMyVotesRequestDto();
        request.setGameIds(List.of("GAME-20", "GAME-20", "GAME-30"));

        when(predictionRepository.findByUserIdAndGameIdIn(
                1L,
                List.of("GAME-20", "GAME-30")
        )).thenReturn(
                List.of(
                        new Prediction("GAME-20", 1L, "home"),
                        new Prediction("GAME-30", 1L, "away")
                )
        );

        ResponseEntity<?> response = controller.getMyVotesBulk(request, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, String> votes = extractVotes(response);
        assertThat(votes).containsOnly(
                Map.entry("GAME-20", "home"),
                Map.entry("GAME-30", "away")
        );
        verify(predictionRepository).findByUserIdAndGameIdIn(1L, List.of("GAME-20", "GAME-30"));
    }

    @Test
    void shouldReturnMissingGamesAsNullAndKnownGamesAsTeamCode() {
        PredictionService predictionService = mock(PredictionService.class);
        PredictionRepository predictionRepository = mock(PredictionRepository.class);
        PredictionController controller =
                new PredictionController(predictionService, predictionRepository);

        Principal principal = () -> "7";
        PredictionMyVotesRequestDto request = new PredictionMyVotesRequestDto();
        request.setGameIds(List.of("GAME-40", "GAME-41", "GAME-42"));

        when(predictionRepository.findByUserIdAndGameIdIn(
                7L,
                List.of("GAME-40", "GAME-41", "GAME-42")
        )).thenReturn(
                List.of(new Prediction("GAME-40", 7L, "home"))
        );

        ResponseEntity<?> response = controller.getMyVotesBulk(request, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, String> votes = extractVotes(response);

        assertThat(votes.get("GAME-40")).isEqualTo("home");
        assertThat(votes).containsEntry("GAME-41", null);
        assertThat(votes).containsEntry("GAME-42", null);
        assertThat(votes).hasSize(3);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> extractVotes(ResponseEntity<?> response) {
        Object bodyObject = response.getBody();
        assertThat(bodyObject).isInstanceOf(Map.class);
        Map<String, Object> body = (Map<String, Object>) bodyObject;
        assertThat(body).isNotNull();
        assertThat(body).containsKey("votes");
        assertInstanceOf(Map.class, body.get("votes"));

        return (Map<String, String>) body.get("votes");
    }
}
