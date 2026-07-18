package com.example.BegaDiary.Service;

import com.example.BegaDiary.Entity.SeatViewClassificationResult;
import com.example.BegaDiary.Service.port.SeatViewClassificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class SeatViewClassificationService {

    private final SeatViewClassificationPort classificationPort;

    public SeatViewClassificationResult classify(MultipartFile file) {
        return classificationPort.classify(file);
    }
}
