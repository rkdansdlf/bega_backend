package com.example.teamRecommendationTest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.common.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class TeamRecommendationTestControllerValidationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TeamRecommendationTestService service;
    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(TeamRecommendationTestService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TeamRecommendationTestController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.destroy();
    }

    @Test
    void missingOrNullAnswersReturnBadRequestBeforeCallingService() throws Exception {
        for (String body : List.of("{}", "{\"answers\":null}")) {
            mockMvc.perform(post("/api/quiz/result")
                            .principal(() -> "42")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors.answers").exists());
        }

        verifyNoInteractions(service);
    }

    @Test
    void emptyAnswersMapKeepsExistingServiceBehavior() throws Exception {
        when(service.CalculateBestTeam(eq(42L), any(TeamUserAnswersDto.class)))
                .thenReturn(new TeamResultDto("테스트 팀", "#000000"));

        mockMvc.perform(post("/api/quiz/result")
                        .principal(() -> "42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("answers", java.util.Map.of()))))
                .andExpect(status().isOk());

        verify(service).CalculateBestTeam(eq(42L), argThat(request -> request.getAnswers().isEmpty()));
    }

    @Test
    void missingInternalTeamsReturnManualDataContract() throws Exception {
        TeamRecommendationTestRepository repository = mock(TeamRecommendationTestRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        MockMvc realServiceMvc = MockMvcBuilders.standaloneSetup(
                        new TeamRecommendationTestController(new TeamRecommendationTestService(repository)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        realServiceMvc.perform(post("/api/quiz/result")
                        .principal(() -> "42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MANUAL_BASEBALL_DATA_REQUIRED"))
                .andExpect(jsonPath("$.data.missingItems[0].key").value("teams"));
    }

    @Test
    void emptyAnswersStillUseAvailableInternalTeams() throws Exception {
        TeamRecommendationTestRepository repository = mock(TeamRecommendationTestRepository.class);
        when(repository.findAll()).thenReturn(List.of(new TeamRecommendationTest("SYNTHETIC", "Synthetic team", "S")));
        MockMvc realServiceMvc = MockMvcBuilders.standaloneSetup(
                        new TeamRecommendationTestController(new TeamRecommendationTestService(repository)))
                .setControllerAdvice(new GlobalExceptionHandler()).setValidator(validator).build();

        realServiceMvc.perform(post("/api/quiz/result").principal(() -> "42")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Synthetic team"));
    }
}
