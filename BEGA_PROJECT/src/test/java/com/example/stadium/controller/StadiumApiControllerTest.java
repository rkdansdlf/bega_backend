package com.example.stadium.controller;

import com.example.stadium.dto.PlaceDto;
import com.example.stadium.dto.StadiumDetailDto;
import com.example.stadium.dto.StadiumDto;
import com.example.stadium.exception.StadiumNotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @DisplayName("카테고리 파라미터가 빈 문자열이면 구장 상세의 places를 반환한다")
    void getPlacesByStadium_withBlankCategory_returnsDetailPlaces() {
        List<PlaceDto> places = List.of(
                PlaceDto.builder().id(2L).name("픽업존").category("delivery").build()
        );
        StadiumDetailDto detail = StadiumDetailDto.builder()
                .stadiumId("JAMSIL")
                .places(places)
                .build();
        when(stadiumService.getStadiumDetail("JAMSIL")).thenReturn(detail);

        ResponseEntity<List<PlaceDto>> response = stadiumApiController.getPlacesByStadium("JAMSIL", "");

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
    @DisplayName("구장 상세 조회 시 존재하지 않는 구장이면 예외를 전파한다")
    void getStadiumDetail_notFound_throwsException() {
        when(stadiumService.getStadiumDetail("UNKNOWN")).thenThrow(new StadiumNotFoundException("UNKNOWN"));

        assertThatThrownBy(() -> stadiumApiController.getStadiumDetail("UNKNOWN"))
                .isInstanceOf(StadiumNotFoundException.class);
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

    @Test
    @DisplayName("즐겨찾기 삭제 요청 시 Principal 사용자 ID를 파싱해 서비스에 전달한다")
    void removeFavorite_parsesPrincipalAndCallsService() {
        Principal principal = () -> "42";

        ResponseEntity<Map<String, Boolean>> response = stadiumApiController.removeFavorite("JAMSIL", principal);

        assertThat(response.getBody()).containsEntry("favourited", false);
        verify(stadiumService).removeFavorite(42L, "JAMSIL");
    }

    @Test
    @DisplayName("즐겨찾기 조회 시 stadiumIds 필드를 포함해 반환한다")
    void getFavorites_returnsStadiumIds() {
        Principal principal = () -> "42";
        when(stadiumService.getFavoriteStadiumIds(42L)).thenReturn(List.of("JAMSIL", "GOCHEOK"));

        ResponseEntity<Map<String, List<String>>> response = stadiumApiController.getFavorites(principal);

        assertThat(response.getBody()).containsKey("stadiumIds");
        assertThat(response.getBody().get("stadiumIds")).containsExactly("JAMSIL", "GOCHEOK");
    }
}
