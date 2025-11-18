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
public class StadiumApiController {

    private final StadiumService stadiumService;

    @GetMapping
    public ResponseEntity<List<StadiumDto>> getStadiums() {
        List<StadiumDto> stadiums = stadiumService.getAllStadiums();
        return ResponseEntity.ok(stadiums);
    }

    @GetMapping("/{stadiumId}")
    public ResponseEntity<StadiumDetailDto> getStadiumDetail(
            @PathVariable("stadiumId") String stadiumId) {  
        return ResponseEntity.ok(stadiumService.getStadiumDetail(stadiumId));
    }

    @GetMapping("/name/{stadiumName}")
    public ResponseEntity<StadiumDetailDto> getStadiumDetailByName(
            @PathVariable("stadiumName") String stadiumName) {;
        return ResponseEntity.ok(stadiumService.getStadiumDetailByName(stadiumName));
    }

    @GetMapping("/{stadiumId}/places")
    public ResponseEntity<List<PlaceDto>> getPlacesByStadium(
            @PathVariable("stadiumId") String stadiumId, 
            @RequestParam(name = "category", required = false) String category) {

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

        if (category != null && !category.isEmpty()) {
            return ResponseEntity.ok(stadiumService.getPlacesByStadiumNameAndCategory(stadiumName, category));
        } else {
            return ResponseEntity.ok(stadiumService.getStadiumDetailByName(stadiumName).getPlaces());
        }
    }

    @GetMapping("/places/all")
    public ResponseEntity<List<PlaceDto>> getAllPlaces() {

        return ResponseEntity.ok(stadiumService.getAllPlaces());
    }
}