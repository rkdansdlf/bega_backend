package com.example.kbo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.BegaDiary.Service.BegaGameService;
import com.example.cheerboard.storage.config.StorageConfig;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.image.ImageOptimizationMetricsService;
import com.example.kbo.dto.TicketInfo;
import com.example.kbo.service.port.TicketVisionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

class TicketAnalysisServiceTest {

    private final TicketVisionPort ticketVisionPort = mock(TicketVisionPort.class);
    private final BegaGameService begaGameService = mock(BegaGameService.class);
    private final StorageConfig storageConfig = mock(StorageConfig.class);
    private final ImageOptimizationMetricsService metricsService = mock(ImageOptimizationMetricsService.class);
    private final TicketAnalysisService service = new TicketAnalysisService(
            ticketVisionPort,
            begaGameService,
            storageConfig,
            metricsService);

    @BeforeEach
    void setUp() {
        when(storageConfig.getMaxImageBytes()).thenReturn(5L * 1024L * 1024L);
    }

    @Test
    void analyzeTicketDelegatesToVisionPortAndEnrichesGameId() {
        MockMultipartFile file = validImage();
        TicketInfo info = TicketInfo.builder()
                .date("2026-07-11")
                .time("18:30")
                .stadium("잠실")
                .homeTeam("LG")
                .awayTeam("두산")
                .build();
        when(ticketVisionPort.analyze(file)).thenReturn(info);
        when(begaGameService.findGameIdByDateAndTeams(
                "2026-07-11", "LG", "두산", "잠실", "18:30"))
                .thenReturn(77L);

        TicketInfo result = service.analyzeTicket(file);

        assertThat(result).isSameAs(info);
        assertThat(result.getGameId()).isEqualTo(77L);
        verify(metricsService).recordRequest("ticket_analyze");
        verify(ticketVisionPort).analyze(file);
    }

    @Test
    void analyzeTicketRejectsInvalidTypeBeforeCallingVisionPort() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "ticket.gif", "image/gif", new byte[] {1});

        assertThatThrownBy(() -> service.analyzeTicket(file))
                .isInstanceOf(BadRequestBusinessException.class)
                .hasMessage("지원되지 않는 이미지 형식입니다. JPG, PNG, WEBP 파일만 가능합니다.");

        verify(metricsService).recordReject("ticket_analyze", "invalid_type");
        verify(ticketVisionPort, never()).analyze(file);
    }

    private MockMultipartFile validImage() {
        return new MockMultipartFile(
                "file", "ticket.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});
    }
}
