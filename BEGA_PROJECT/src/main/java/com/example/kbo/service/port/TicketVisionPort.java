package com.example.kbo.service.port;

import com.example.kbo.dto.TicketInfo;
import org.springframework.web.multipart.MultipartFile;

@FunctionalInterface
public interface TicketVisionPort {

    TicketInfo analyze(MultipartFile file);
}
