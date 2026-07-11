package com.example.stadium.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.stadium.dto.PlaceDto;
import com.example.stadium.entity.Place;
import com.example.stadium.entity.Stadium;
import com.example.stadium.exception.StadiumNotFoundException;
import com.example.stadium.repository.PlaceRepository;
import com.example.stadium.repository.StadiumRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StadiumAdminServiceTest {

    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final StadiumRepository stadiumRepository = mock(StadiumRepository.class);
    private final StadiumAdminService service = new StadiumAdminService(placeRepository, stadiumRepository);

    @Test
    void createPlacePersistsAndReturnsDto() {
        Stadium stadium = mock(Stadium.class);
        when(stadium.getStadiumName()).thenReturn("잠실야구장");
        when(stadiumRepository.findById("JAMSIL")).thenReturn(Optional.of(stadium));
        when(placeRepository.save(any(Place.class))).thenAnswer(invocation -> {
            Place place = invocation.getArgument(0);
            place.setId(1L);
            return place;
        });

        PlaceDto result = service.createPlace("JAMSIL", command());

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getStadiumName()).isEqualTo("잠실야구장");
        assertThat(result.getName()).isEqualTo("통밥");
        assertThat(result.getRating()).isEqualTo(4.5);
    }

    @Test
    void createPlaceRejectsUnknownStadium() {
        when(stadiumRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createPlace("UNKNOWN", command()))
                .isInstanceOf(StadiumNotFoundException.class);
    }

    @Test
    void updatePlaceMutatesAndReturnsDto() {
        Stadium stadium = mock(Stadium.class);
        when(stadium.getStadiumName()).thenReturn("잠실야구장");
        Place place = Place.builder()
                .id(1L)
                .stadium(stadium)
                .name("기존")
                .category("old")
                .lat(0.0)
                .lng(0.0)
                .rating(BigDecimal.ONE)
                .build();
        when(placeRepository.findById(1L)).thenReturn(Optional.of(place));
        when(placeRepository.save(place)).thenReturn(place);

        PlaceDto result = service.updatePlace(1L, command());

        assertThat(result.getName()).isEqualTo("통밥");
        assertThat(result.getCategory()).isEqualTo("food");
        verify(placeRepository).save(place);
    }

    @Test
    void deletePlacePreservesNotFoundContract() {
        when(placeRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.deletePlace(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("장소를 찾을 수 없습니다. id=99");
    }

    private StadiumPlaceCommand command() {
        return new StadiumPlaceCommand(
                "통밥",
                "food",
                "설명",
                "서울",
                "02-0000-0000",
                37.5,
                127.0,
                4.5,
                "10:00",
                "22:00");
    }
}
