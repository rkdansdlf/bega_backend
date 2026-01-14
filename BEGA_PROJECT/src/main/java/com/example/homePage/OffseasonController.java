package com.example.homePage;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/kbo/offseason")
@RequiredArgsConstructor
public class OffseasonController {

    private final OffseasonService offseasonService;

    @GetMapping("/movements")
    public ResponseEntity<List<OffseasonMovementDto>> getMovements() {
        return ResponseEntity.ok(offseasonService.getOffseasonMovements());
    }

    @GetMapping("/metadata")
    public ResponseEntity<OffseasonMetaDto> getMetadata(
            @RequestParam(defaultValue = "2025") int year) {
        return ResponseEntity.ok(offseasonService.getOffseasonMetadata(year));
    }
}
