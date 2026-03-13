package com.example.admin.controller;

import com.example.admin.dto.OffseasonMovementAdminDto;
import com.example.admin.exception.OffseasonMovementNotFoundException;
import com.example.admin.service.OffseasonMovementAdminService;
import com.example.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OffseasonMovementAdminControllerTest {

    private MockMvc mockMvc;
    private OffseasonMovementAdminService service;

    @BeforeEach
    void setUp() {
        service = mock(OffseasonMovementAdminService.class);
        OffseasonMovementAdminController controller = new OffseasonMovementAdminController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("스토브리그 이동 목록 조회 성공")
    void getMovementsSuccess() throws Exception {
        given(service.getMovements(eq("홍길동"), eq(null), eq("LG"), eq(null), eq(null)))
                .willReturn(List.of(
                        OffseasonMovementAdminDto.builder()
                                .id(3L)
                                .movementDate(LocalDate.of(2025, 1, 12))
                                .teamCode("LG")
                                .playerName("홍길동")
                                .summary("4년 총액 80억에 잔류")
                                .build()));

        mockMvc.perform(get("/api/admin/offseason/movements")
                        .param("search", "홍길동")
                        .param("teamCode", "LG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(3))
                .andExpect(jsonPath("$.data[0].teamCode").value("LG"));
    }

    @Test
    @DisplayName("스토브리그 이동 등록 성공")
    void createMovementSuccess() throws Exception {
        given(service.createMovement(org.mockito.ArgumentMatchers.any()))
                .willReturn(OffseasonMovementAdminDto.builder()
                        .id(7L)
                        .teamCode("LG")
                        .playerName("홍길동")
                        .summary("4년 총액 80억에 잔류")
                        .build());

        mockMvc.perform(post("/api/admin/offseason/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "movementDate": "2025-01-15",
                                  "section": "FA",
                                  "teamCode": "LG",
                                  "playerName": "홍길동",
                                  "summary": "4년 총액 80억에 잔류"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.id").value(7));
    }

    @Test
    @DisplayName("스토브리그 이동 등록 validation 실패 시 errors 맵을 반환")
    void createMovementValidationFailureReturnsErrorsMap() throws Exception {
        mockMvc.perform(post("/api/admin/offseason/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "movementDate": "2025-01-15",
                                  "teamCode": "",
                                  "playerName": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("입력값을 확인해주세요."))
                .andExpect(jsonPath("$.errors.section").value("구분은 필수입니다."))
                .andExpect(jsonPath("$.errors.teamCode").value("팀 코드는 필수입니다."))
                .andExpect(jsonPath("$.errors.playerName").value("선수명은 필수입니다."));
    }

    @Test
    @DisplayName("잘못된 날짜 파라미터는 validation 에러 응답으로 표준화된다")
    void invalidDateQueryReturnsValidationErrorResponse() throws Exception {
        mockMvc.perform(get("/api/admin/offseason/movements")
                        .param("fromDate", "bad-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.fromDate").value("fromDate 값 형식이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("스토브리그 이동 삭제 시 존재하지 않으면 404")
    void deleteMovementNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new OffseasonMovementNotFoundException(99L))
                .when(service)
                .deleteMovement(99L);

        mockMvc.perform(delete("/api/admin/offseason/movements/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("OFFSEASON_MOVEMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("이동 정보를 찾을 수 없습니다. id=99"));
    }
}
