package com.example.mate.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.common.exception.GlobalExceptionHandler;
import com.example.mate.dto.CheckInRecordDTO;
import com.example.mate.service.CheckInRecordService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CheckInRecordControllerValidationTest {

    private CheckInRecordService checkInRecordService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        checkInRecordService = mock(CheckInRecordService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CheckInRecordController(checkInRecordService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(42L, null, "ROLE_USER"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void defaultsToPageZeroAndThirtyRowsAndReturnsPageMetadata() throws Exception {
        CheckInRecordDTO.HistoryPageResponse response = CheckInRecordDTO.HistoryPageResponse.builder()
                .content(List.of())
                .page(0)
                .size(30)
                .hasNext(true)
                .build();
        when(checkInRecordService.getCheckInsByUserId(eq(42L), eq(42L), any(Pageable.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/checkin/user/42").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(30))
                .andExpect(jsonPath("$.hasNext").value(true));

        verify(checkInRecordService).getCheckInsByUserId(42L, 42L, PageRequest.of(0, 30));
    }

    @Test
    void acceptsMaximumPageSize() throws Exception {
        when(checkInRecordService.getCheckInsByUserId(eq(42L), eq(42L), any(Pageable.class)))
                .thenReturn(CheckInRecordDTO.HistoryPageResponse.builder()
                        .content(List.of())
                        .page(0)
                        .size(100)
                        .hasNext(false)
                        .build());

        mockMvc.perform(get("/api/checkin/user/42").param("size", "100"))
                .andExpect(status().isOk());

        verify(checkInRecordService).getCheckInsByUserId(42L, 42L, PageRequest.of(0, 100));
    }

    @Test
    void rejectsInvalidPageAndSizeBeforeCallingTheService() throws Exception {
        mockMvc.perform(get("/api/checkin/user/42").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/checkin/user/42").param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/checkin/user/42").param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/checkin/user/42").param("page", "2147483647").param("size", "30"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/checkin/user/42").param("page", "71582789").param("size", "30"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(checkInRecordService);
    }

    @Test
    void acceptsLargestRepresentableOffsets() throws Exception {
        when(checkInRecordService.getCheckInsByUserId(eq(42L), eq(42L), any(Pageable.class)))
                .thenReturn(CheckInRecordDTO.HistoryPageResponse.builder().content(List.of()).build());

        mockMvc.perform(get("/api/checkin/user/42").param("page", "71582788").param("size", "30"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/checkin/user/42").param("page", "2147483647").param("size", "1"))
                .andExpect(status().isOk());

        verify(checkInRecordService).getCheckInsByUserId(42L, 42L, PageRequest.of(71582788, 30));
        verify(checkInRecordService).getCheckInsByUserId(42L, 42L, PageRequest.of(Integer.MAX_VALUE, 1));
    }
}
