package com.example.kbo.service;

import com.example.BegaDiary.Service.BegaGameService;
import com.example.cheerboard.storage.config.StorageConfig;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.image.ImageOptimizationMetricsService;
import com.example.kbo.dto.TicketInfo;
import com.example.kbo.exception.TicketAnalysisException;
import com.example.kbo.service.port.TicketVisionPort;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketAnalysisService {

    private final TicketVisionPort ticketVisionPort;
    private final BegaGameService begaGameService;
    private final StorageConfig storageConfig;
    private final ImageOptimizationMetricsService metricsService;

    public TicketInfo analyzeTicket(MultipartFile file) {
        log.info("Analyzing ticket image: {}", file != null ? file.getOriginalFilename() : null);
        metricsService.recordRequest("ticket_analyze");
        validateTicketImage(file);
        try {
            return enrichTicketInfoWithGameId(ticketVisionPort.analyze(file));
        } catch (TicketAnalysisException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("AI Service call failed", exception);
            throw new TicketAnalysisException(
                    HttpStatus.BAD_GATEWAY,
                    "티켓 분석 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private TicketInfo enrichTicketInfoWithGameId(TicketInfo info) {
        if (info == null) {
            return null;
        }

        if (info.getDate() != null && info.getHomeTeam() != null && info.getAwayTeam() != null) {
            Long gameId = begaGameService.findGameIdByDateAndTeams(
                    info.getDate(),
                    info.getHomeTeam(),
                    info.getAwayTeam(),
                    info.getStadium(),
                    info.getTime());
            info.setGameId(gameId);
            log.info("Matched game ID: {}", gameId);
        }

        return Objects.requireNonNull(info);
    }

    private void validateTicketImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            metricsService.recordReject("ticket_analyze", "file_required");
            throw new BadRequestBusinessException("TICKET_IMAGE_REQUIRED", "업로드할 티켓 이미지가 없습니다.");
        }
        if (file.getSize() > storageConfig.getMaxImageBytes()) {
            metricsService.recordReject("ticket_analyze", "file_too_large");
            throw new BadRequestBusinessException("TICKET_IMAGE_TOO_LARGE", "이미지 파일 크기가 너무 큽니다. (최대 5MB)");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType)
                || (!MediaType.IMAGE_JPEG_VALUE.equals(contentType)
                        && !MediaType.IMAGE_PNG_VALUE.equals(contentType)
                        && "image/webp".equals(contentType) == false)) {
            metricsService.recordReject("ticket_analyze", "invalid_type");
            throw new BadRequestBusinessException("TICKET_IMAGE_INVALID_TYPE",
                    "지원되지 않는 이미지 형식입니다. JPG, PNG, WEBP 파일만 가능합니다.");
        }
    }
}
