package com.example.stadium.service;

import com.example.stadium.dto.PlaceDto;
import com.example.stadium.dto.StadiumDetailDto;
import com.example.stadium.dto.StadiumDto;
import com.example.stadium.entity.Place;
import com.example.stadium.entity.Stadium;
import com.example.stadium.entity.UserStadiumFavorite;
import com.example.stadium.repository.PlaceRepository;
import com.example.stadium.repository.StadiumRepository;
import com.example.stadium.repository.UserStadiumFavoriteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StadiumServiceTest {

    @Mock
    private StadiumRepository stadiumRepository;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private UserStadiumFavoriteRepository favoriteRepository;

    @InjectMocks
    private StadiumService stadiumService;

    @Test
    @DisplayName("구장 목록 조회 시 좌표가 없는 구장은 제외된다")
    void getAllStadiums_filtersOutWithoutCoordinates() {
        Stadium valid = Stadium.builder()
                .stadiumId("JAMSIL")
                .stadiumName("잠실야구장")
                .team("LG/두산")
                .lat(37.5122)
                .lng(127.0719)
                .address("서울특별시 송파구 올림픽로 25")
                .build();
        Stadium invalid = Stadium.builder()
                .stadiumId("UNKNOWN")
                .stadiumName("좌표미등록구장")
                .lat(null)
                .lng(127.0)
                .build();

        when(stadiumRepository.findAll()).thenReturn(List.of(valid, invalid));

        List<StadiumDto> result = stadiumService.getAllStadiums();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStadiumId()).isEqualTo("JAMSIL");
        assertThat(result.get(0).getStadiumName()).isEqualTo("잠실야구장");
    }

    @Test
    @DisplayName("구장 상세 조회 시 장소 평점과 운영시간이 DTO로 변환된다")
    void getStadiumDetail_mapsPlaceMetadata() {
        Stadium stadium = Stadium.builder()
                .stadiumId("JAMSIL")
                .stadiumName("잠실야구장")
                .team("LG/두산")
                .lat(37.5122)
                .lng(127.0719)
                .address("서울특별시 송파구 올림픽로 25")
                .build();
        Place place = Place.builder()
                .id(1L)
                .stadium(stadium)
                .category("food")
                .name("통밥")
                .description("대표 메뉴")
                .lat(37.5122)
                .lng(127.0719)
                .address("서울특별시 송파구 올림픽로 25")
                .rating(new BigDecimal("4.5"))
                .openTime("10:30")
                .closeTime("22:00")
                .build();

        when(stadiumRepository.findById("JAMSIL")).thenReturn(Optional.of(stadium));
        when(placeRepository.findByStadiumIdWithSort("JAMSIL")).thenReturn(List.of(place));

        StadiumDetailDto result = stadiumService.getStadiumDetail("JAMSIL");

        assertThat(result.getStadiumId()).isEqualTo("JAMSIL");
        assertThat(result.getPlaces()).hasSize(1);
        PlaceDto mapped = result.getPlaces().get(0);
        assertThat(mapped.getRating()).isEqualTo(4.5);
        assertThat(mapped.getOpenTime()).isEqualTo("10:30");
        assertThat(mapped.getCloseTime()).isEqualTo("22:00");
    }

    @Test
    @DisplayName("즐겨찾기 추가 시 중복이 아니면 저장된다")
    void addFavorite_savesWhenNotExists() {
        when(favoriteRepository.existsByUserIdAndStadiumId(1L, "JAMSIL")).thenReturn(false);

        stadiumService.addFavorite(1L, "JAMSIL");

        verify(favoriteRepository).save(any(UserStadiumFavorite.class));
    }

    @Test
    @DisplayName("즐겨찾기 추가 시 이미 존재하면 저장하지 않는다")
    void addFavorite_doesNotSaveWhenAlreadyExists() {
        when(favoriteRepository.existsByUserIdAndStadiumId(1L, "JAMSIL")).thenReturn(true);

        stadiumService.addFavorite(1L, "JAMSIL");

        verify(favoriteRepository, never()).save(any(UserStadiumFavorite.class));
    }

    @Test
    @DisplayName("사용자 즐겨찾기 구장 ID 목록을 반환한다")
    void getFavoriteStadiumIds_returnsIds() {
        when(favoriteRepository.findByUserId(3L)).thenReturn(List.of(
                new UserStadiumFavorite(3L, "JAMSIL"),
                new UserStadiumFavorite(3L, "GOCHEOK")
        ));

        List<String> result = stadiumService.getFavoriteStadiumIds(3L);

        assertThat(result).containsExactly("JAMSIL", "GOCHEOK");
    }
}
