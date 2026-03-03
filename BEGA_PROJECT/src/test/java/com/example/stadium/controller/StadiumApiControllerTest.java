package com.example.stadium.controller;

import com.example.stadium.dto.PlaceDto;
import com.example.stadium.dto.StadiumDetailDto;
import com.example.stadium.dto.StadiumDto;
import com.example.stadium.service.StadiumService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StadiumApiControllerTest {

    @Mock
    private StadiumService stadiumService;

    @InjectMocks
    private StadiumApiController stadiumApiController;

    @Test
    @DisplayName("카테고리 파라미터가 있으면 카테고리 전용 장소 조회를 호출한다")
    void getPlacesByStadium_withCategory_callsCategoryService() {
        List<PlaceDto> places = List.of(
                PlaceDto.builder().id(1L).name("통밥").category("food").build()
        );
        when(stadiumService.getPlacesByStadiumAndCategory("JAMSIL", "food")).thenReturn(places);

        ResponseEntity<List<PlaceDto>> response = stadiumApiController.getPlacesByStadium("JAMSIL", "food");

        assertThat(response.getBody()).isEqualTo(places);
        verify(stadiumService).getPlacesByStadiumAndCategory("JAMSIL", "food");
    }

    @Test
    @DisplayName("카테고리 파라미터가 없으면 구장 상세의 places를 반환한다")
    void getPlacesByStadium_withoutCategory_returnsDetailPlaces() {
        List<PlaceDto> places = List.of(
                PlaceDto.builder().id(2L).name("픽업존").category("delivery").build()
        );
        StadiumDetailDto detail = StadiumDetailDto.builder()
                .stadiumId("JAMSIL")
                .places(places)
                .build();

        when(stadiumService.getStadiumDetail("JAMSIL")).thenReturn(detail);

        ResponseEntity<List<PlaceDto>> response = stadiumApiController.getPlacesByStadium("JAMSIL", null);

        assertThat(response.getBody()).isEqualTo(places);
        verify(stadiumService).getStadiumDetail("JAMSIL");
    }

    @Test
    @DisplayName("즐겨찾기 추가 요청 시 Principal 사용자 ID를 파싱해 서비스에 전달한다")
    void addFavorite_parsesPrincipalAndCallsService() {
        Principal principal = () -> "42";

        ResponseEntity<Map<String, Boolean>> response = stadiumApiController.addFavorite("JAMSIL", principal);

        assertThat(response.getBody()).containsEntry("favourited", true);
        verify(stadiumService).addFavorite(42L, "JAMSIL");
    }

    @Test
    @DisplayName("구장 목록 조회 시 서비스 결과를 그대로 반환한다")
    void getStadiums_returnsServiceResult() {
        List<StadiumDto> stadiums = List.of(
                StadiumDto.builder().stadiumId("JAMSIL").stadiumName("잠실야구장").build()
        );
        when(stadiumService.getAllStadiums()).thenReturn(stadiums);

        ResponseEntity<List<StadiumDto>> response = stadiumApiController.getStadiums();

        assertThat(response.getBody()).isEqualTo(stadiums);
    }
}
