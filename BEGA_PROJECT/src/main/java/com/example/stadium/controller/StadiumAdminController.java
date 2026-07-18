package com.example.stadium.controller;

import com.example.common.dto.ApiResponse;
import com.example.stadium.dto.PlaceDto;
import com.example.stadium.service.StadiumAdminService;
import com.example.stadium.service.StadiumPlaceCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 구장 장소 관리자 CRUD API
 * ADMIN 이상 권한 필요
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/admin/stadiums", produces = "application/json; charset=UTF-8")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class StadiumAdminController {

    private final StadiumAdminService stadiumAdminService;

    // ───────────────────────────────────────────
    // Request DTO
    // ───────────────────────────────────────────

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PlaceRequest {

        @NotBlank(message = "장소 이름은 필수입니다.")
        @Size(max = 100, message = "이름은 100자 이하여야 합니다.")
        private String name;

        @NotBlank(message = "카테고리는 필수입니다.")
        @Size(max = 50, message = "카테고리는 50자 이하여야 합니다.")
        private String category;

        private String description;

        @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
        private String address;

        @Size(max = 20, message = "전화번호는 20자 이하여야 합니다.")
        private String phone;

        @NotNull(message = "위도는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
        private Double lat;

        @NotNull(message = "경도는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
        private Double lng;

        @DecimalMin(value = "0.0", message = "평점은 0.0 이상이어야 합니다.")
        @DecimalMax(value = "5.0", message = "평점은 5.0 이하여야 합니다.")
        private Double rating;

        @Size(max = 50, message = "오픈 시간은 50자 이하여야 합니다.")
        private String openTime;

        @Size(max = 50, message = "마감 시간은 50자 이하여야 합니다.")
        private String closeTime;
    }

    // ───────────────────────────────────────────
    // POST /api/admin/stadiums/{stadiumId}/places
    // ───────────────────────────────────────────

    /**
     * 구장에 새 장소를 추가합니다.
     * POST /api/admin/stadiums/{stadiumId}/places
     */
    @PostMapping("/{stadiumId}/places")
    public ResponseEntity<ApiResponse> createPlace(
            @PathVariable("stadiumId") String stadiumId,
            @Valid @RequestBody PlaceRequest request) {

        log.info("구장 장소 추가 요청: stadiumId={}, name={}", stadiumId, request.getName());

        PlaceDto dto = stadiumAdminService.createPlace(stadiumId, toCommand(request));

        log.info("구장 장소 추가 완료: placeId={}", dto.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("장소가 추가되었습니다.", dto));
    }

    // ───────────────────────────────────────────
    // PUT /api/admin/stadiums/places/{placeId}
    // ───────────────────────────────────────────

    /**
     * 기존 장소 정보를 수정합니다.
     * PUT /api/admin/stadiums/places/{placeId}
     */
    @PutMapping("/places/{placeId}")
    public ResponseEntity<ApiResponse> updatePlace(
            @PathVariable("placeId") Long placeId,
            @Valid @RequestBody PlaceRequest request) {

        log.info("구장 장소 수정 요청: placeId={}, name={}", placeId, request.getName());

        PlaceDto dto = stadiumAdminService.updatePlace(placeId, toCommand(request));

        log.info("구장 장소 수정 완료: placeId={}", placeId);
        return ResponseEntity.ok(ApiResponse.success("장소가 수정되었습니다.", dto));
    }

    // ───────────────────────────────────────────
    // DELETE /api/admin/stadiums/places/{placeId}
    // ───────────────────────────────────────────

    /**
     * 장소를 삭제합니다.
     * DELETE /api/admin/stadiums/places/{placeId}
     */
    @DeleteMapping("/places/{placeId}")
    public ResponseEntity<ApiResponse> deletePlace(
            @PathVariable("placeId") Long placeId) {

        log.info("구장 장소 삭제 요청: placeId={}", placeId);

        stadiumAdminService.deletePlace(placeId);

        log.info("구장 장소 삭제 완료: placeId={}", placeId);
        return ResponseEntity.ok(ApiResponse.success("장소가 삭제되었습니다."));
    }

    // ───────────────────────────────────────────
    // Helper
    // ───────────────────────────────────────────

    private StadiumPlaceCommand toCommand(PlaceRequest request) {
        return new StadiumPlaceCommand(
                request.getName(),
                request.getCategory(),
                request.getDescription(),
                request.getAddress(),
                request.getPhone(),
                request.getLat(),
                request.getLng(),
                request.getRating(),
                request.getOpenTime(),
                request.getCloseTime());
    }
}
