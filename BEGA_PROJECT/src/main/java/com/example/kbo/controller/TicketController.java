package com.example.kbo.controller;

import com.example.kbo.dto.TicketInfo;
import com.example.kbo.service.TicketAnalysisService;
import com.example.kbo.service.TicketVerificationTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

@Slf4j
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketAnalysisService ticketAnalysisService;
    private final TicketVerificationTokenStore verificationTokenStore;

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TicketInfo> analyzeTicket(@RequestPart("file") MultipartFile file) {
        log.info("Received ticket analysis request");
        TicketInfo ticketInfo = ticketAnalysisService.analyzeTicket(file);

        // Only issue a verification token if OCR extracted meaningful data
        boolean hasMeaningfulData = (ticketInfo.getDate() != null && !ticketInfo.getDate().isBlank())
                || (ticketInfo.getStadium() != null && !ticketInfo.getStadium().isBlank());
        if (hasMeaningfulData) {
            String token = verificationTokenStore.generateToken(ticketInfo);
            ticketInfo.setVerificationToken(token);
        }

        return ResponseEntity.ok(ticketInfo);
    }
}
