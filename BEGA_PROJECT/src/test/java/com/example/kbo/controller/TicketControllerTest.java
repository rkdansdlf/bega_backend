package com.example.kbo.controller;

import com.example.kbo.dto.TicketInfo;
import com.example.kbo.service.TicketAnalysisService;
import com.example.kbo.service.TicketVerificationTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketControllerTest {

        private MockMvc mockMvc;
        private TicketAnalysisService ticketAnalysisService;
        private TicketVerificationTokenStore verificationTokenStore;

        @BeforeEach
        void setup() {
                ticketAnalysisService = mock(TicketAnalysisService.class);
                verificationTokenStore = mock(TicketVerificationTokenStore.class);
                TicketController ticketController = new TicketController(ticketAnalysisService, verificationTokenStore);
                mockMvc = MockMvcBuilders.standaloneSetup(ticketController).build();
        }

        @Test
        @DisplayName("티켓 이미지 분석 요청 성공")
        void analyzeTicketSuccess() throws Exception {
                // given
                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "ticket.jpg",
                                MediaType.IMAGE_JPEG_VALUE,
                                "fake image content".getBytes());

                TicketInfo mockTicketInfo = TicketInfo.builder()
                                .date("2024-05-05")
                                .stadium("Jamsil")
                                .homeTeam("LG")
                                .awayTeam("Doosan")
                                .build();

                given(ticketAnalysisService.analyzeTicket(any())).willReturn(mockTicketInfo);
                given(verificationTokenStore.generateToken(any())).willReturn("test-token-uuid");

                // when & then
                mockMvc.perform(multipart("/api/tickets/analyze")
                                .file(file))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.date").value("2024-05-05"))
                                .andExpect(jsonPath("$.stadium").value("Jamsil"))
                                .andExpect(jsonPath("$.homeTeam").value("LG"))
                                .andExpect(jsonPath("$.verificationToken").value("test-token-uuid"));
        }
}
