package com.example.homepage;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomePageFacadeService homePageFacadeService;

    @GetMapping("/bootstrap")
    public ResponseEntity<HomeBootstrapResponseDto> getBootstrap(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate selectedDate = date == null ? LocalDate.now() : date;
        return ResponseEntity.ok(homePageFacadeService.getBootstrap(selectedDate));
    }

    @GetMapping("/widgets")
    public ResponseEntity<HomeWidgetsResponseDto> getWidgets(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate selectedDate = date == null ? LocalDate.now() : date;
        return ResponseEntity.ok(homePageFacadeService.getWidgets(selectedDate));
    }
}
