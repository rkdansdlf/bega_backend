package com.example.BegaDiary.Service.port;

import com.example.BegaDiary.Entity.SeatViewClassificationResult;
import org.springframework.web.multipart.MultipartFile;

@FunctionalInterface
public interface SeatViewClassificationPort {

    SeatViewClassificationResult classify(MultipartFile file);
}
