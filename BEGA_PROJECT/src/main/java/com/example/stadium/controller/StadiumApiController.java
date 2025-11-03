package com.example.stadium.controller;

import com.example.stadium.dto.PlaceDto;
import com.example.stadium.dto.StadiumDetailDto;
import com.example.stadium.dto.StadiumDto;
import com.example.stadium.service.StadiumService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping(value = "/api/stadiums", produces = "application/json; charset=UTF-8")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"},
        allowCredentials = "true",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class StadiumApiController {

    private final StadiumService stadiumService;

    @GetMapping
    public ResponseEntity<List<StadiumDto>> getStadiums() {
        log.debug("구장 목록 조회 요청");
        List<StadiumDto> stadiums = stadiumService.getAllStadiums();
        log.debug("구장 목록 조회 성공: {}개", stadiums.size());
        return ResponseEntity.ok(stadiums);
    }

    @GetMapping("/{stadiumId}")
    public ResponseEntity<StadiumDetailDto> getStadiumDetail(
            @PathVariable("stadiumId") String stadiumId) {  
        log.debug("구장 상세 조회 요청: ID={}", stadiumId);
        return ResponseEntity.ok(stadiumService.getStadiumDetail(stadiumId));
    }

    @GetMapping("/name/{stadiumName}")
    public ResponseEntity<StadiumDetailDto> getStadiumDetailByName(
            @PathVariable("stadiumName") String stadiumName) {
        log.debug("구장 상세 조회 요청: 이름={}", stadiumName);
        return ResponseEntity.ok(stadiumService.getStadiumDetailByName(stadiumName));
    }

    @GetMapping("/{stadiumId}/places")
    public ResponseEntity<List<PlaceDto>> getPlacesByStadium(
            @PathVariable("stadiumId") String stadiumId,  // Long → String
            @RequestParam(name = "category", required = false) String category) {

        log.debug("구장 장소 조회 요청: stadiumId={}, category={}", stadiumId, category);

        if (category != null && !category.isEmpty()) {
            return ResponseEntity.ok(stadiumService.getPlacesByStadiumAndCategory(stadiumId, category));
        } else {
            return ResponseEntity.ok(stadiumService.getStadiumDetail(stadiumId).getPlaces());
        }
    }

    @GetMapping("/name/{stadiumName}/places")
    public ResponseEntity<List<PlaceDto>> getPlacesByStadiumName(
            @PathVariable("stadiumName") String stadiumName,
            @RequestParam(name = "category", required = false) String category) {

        log.debug("구장 장소 조회 요청: stadiumName={}, category={}", stadiumName, category);

        if (category != null && !category.isEmpty()) {
            return ResponseEntity.ok(stadiumService.getPlacesByStadiumNameAndCategory(stadiumName, category));
        } else {
            return ResponseEntity.ok(stadiumService.getStadiumDetailByName(stadiumName).getPlaces());
        }
    }

    @GetMapping("/places/all")
    public ResponseEntity<List<PlaceDto>> getAllPlaces() {
        log.debug("전체 장소 조회 요청");
        return ResponseEntity.ok(stadiumService.getAllPlaces());
    }
}