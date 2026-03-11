package com.example.admin.controller;

import com.example.admin.dto.OffseasonMovementAdminDto;
import com.example.admin.service.OffseasonMovementAdminService;
import com.example.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.http.HttpStatus.NOT_FOUND;
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
                .andExpect(jsonPath("$.data.id").value(7));
    }

    @Test
    @DisplayName("스토브리그 이동 삭제 시 존재하지 않으면 404")
    void deleteMovementNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new ResponseStatusException(NOT_FOUND, "이동 정보를 찾을 수 없습니다. id=99"))
                .when(service)
                .deleteMovement(99L);

        mockMvc.perform(delete("/api/admin/offseason/movements/99"))
                .andExpect(status().isNotFound());
    }
}
