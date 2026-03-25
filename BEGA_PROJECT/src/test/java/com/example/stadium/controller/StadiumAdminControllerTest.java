package com.example.stadium.controller;

import com.example.common.dto.ApiResponse;
import com.example.stadium.entity.Place;
import com.example.stadium.entity.Stadium;
import com.example.stadium.exception.StadiumNotFoundException;
import com.example.stadium.repository.PlaceRepository;
import com.example.stadium.repository.StadiumRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StadiumAdminControllerTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private StadiumRepository stadiumRepository;

    @InjectMocks
    private StadiumAdminController controller;

    private StadiumAdminController.PlaceRequest createRequest() {
        StadiumAdminController.PlaceRequest req = new StadiumAdminController.PlaceRequest();
        req.setName("통밥");
        req.setCategory("food");
        req.setLat(37.5);
        req.setLng(127.0);
        return req;
    }

    // ── createPlace ──

    @Test
    @DisplayName("장소 추가 시 201을 반환한다")
    void createPlace_happyPath_returns201() {
        Stadium stadium = mock(Stadium.class);
        when(stadiumRepository.findById("JAMSIL")).thenReturn(Optional.of(stadium));

        Place saved = mock(Place.class);
        when(saved.getId()).thenReturn(1L);
        when(saved.getStadium()).thenReturn(stadium);
        when(stadium.getStadiumName()).thenReturn("잠실야구장");
        when(placeRepository.save(any(Place.class))).thenReturn(saved);

        ResponseEntity<ApiResponse> result = controller.createPlace("JAMSIL", createRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().isSuccess()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 구장이면 예외를 던진다")
    void createPlace_stadiumNotFound_throwsException() {
        when(stadiumRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.createPlace("UNKNOWN", createRequest()))
                .isInstanceOf(StadiumNotFoundException.class);
    }

    // ── updatePlace ──

    @Test
    @DisplayName("장소 수정 시 200을 반환한다")
    void updatePlace_happyPath_returnsOk() {
        Place place = mock(Place.class);
        Stadium stadium = mock(Stadium.class);
        when(place.getStadium()).thenReturn(stadium);
        when(stadium.getStadiumName()).thenReturn("잠실야구장");
        when(placeRepository.findById(1L)).thenReturn(Optional.of(place));
        when(placeRepository.save(place)).thenReturn(place);

        ResponseEntity<ApiResponse> result = controller.updatePlace(1L, createRequest());

        assertThat(result.getBody().isSuccess()).isTrue();
        verify(placeRepository).save(place);
    }

    @Test
    @DisplayName("존재하지 않는 장소 수정 시 예외를 던진다")
    void updatePlace_placeNotFound_throwsException() {
        when(placeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.updatePlace(999L, createRequest()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── deletePlace ──

    @Test
    @DisplayName("장소 삭제 시 200을 반환한다")
    void deletePlace_happyPath_returnsOk() {
        when(placeRepository.existsById(1L)).thenReturn(true);

        ResponseEntity<ApiResponse> result = controller.deletePlace(1L);

        assertThat(result.getBody().isSuccess()).isTrue();
        verify(placeRepository).deleteById(1L);
    }

    @Test
    @DisplayName("존재하지 않는 장소 삭제 시 예외를 던진다")
    void deletePlace_placeNotFound_throwsException() {
        when(placeRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> controller.deletePlace(999L))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
