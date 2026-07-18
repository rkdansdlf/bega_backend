package com.example.stadium.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.dto.ApiResponse;
import com.example.stadium.dto.PlaceDto;
import com.example.stadium.exception.StadiumNotFoundException;
import com.example.stadium.service.StadiumAdminService;
import com.example.stadium.service.StadiumPlaceCommand;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class StadiumAdminControllerTest {

    @Mock
    private StadiumAdminService stadiumAdminService;

    @InjectMocks
    private StadiumAdminController controller;

    @Test
    @DisplayName("장소 추가 시 201을 반환한다")
    void createPlace_happyPath_returns201() {
        when(stadiumAdminService.createPlace(any(), any())).thenReturn(placeDto());

        ResponseEntity<ApiResponse> result = controller.createPlace("JAMSIL", createRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().isSuccess()).isTrue();
        ArgumentCaptor<StadiumPlaceCommand> command = ArgumentCaptor.forClass(StadiumPlaceCommand.class);
        verify(stadiumAdminService).createPlace(org.mockito.ArgumentMatchers.eq("JAMSIL"), command.capture());
        assertThat(command.getValue().name()).isEqualTo("통밥");
        assertThat(command.getValue().category()).isEqualTo("food");
    }

    @Test
    @DisplayName("존재하지 않는 구장이면 예외를 던진다")
    void createPlace_stadiumNotFound_throwsException() {
        when(stadiumAdminService.createPlace(any(), any()))
                .thenThrow(new StadiumNotFoundException("UNKNOWN"));

        assertThatThrownBy(() -> controller.createPlace("UNKNOWN", createRequest()))
                .isInstanceOf(StadiumNotFoundException.class);
    }

    @Test
    @DisplayName("장소 수정 시 200을 반환한다")
    void updatePlace_happyPath_returnsOk() {
        when(stadiumAdminService.updatePlace(any(), any())).thenReturn(placeDto());

        ResponseEntity<ApiResponse> result = controller.updatePlace(1L, createRequest());

        assertThat(result.getBody().isSuccess()).isTrue();
        verify(stadiumAdminService).updatePlace(org.mockito.ArgumentMatchers.eq(1L), any());
    }

    @Test
    @DisplayName("존재하지 않는 장소 수정 시 예외를 던진다")
    void updatePlace_placeNotFound_throwsException() {
        when(stadiumAdminService.updatePlace(any(), any()))
                .thenThrow(new EntityNotFoundException("장소를 찾을 수 없습니다. id=999"));

        assertThatThrownBy(() -> controller.updatePlace(999L, createRequest()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("장소 삭제 시 200을 반환한다")
    void deletePlace_happyPath_returnsOk() {
        ResponseEntity<ApiResponse> result = controller.deletePlace(1L);

        assertThat(result.getBody().isSuccess()).isTrue();
        verify(stadiumAdminService).deletePlace(1L);
    }

    @Test
    @DisplayName("존재하지 않는 장소 삭제 시 예외를 던진다")
    void deletePlace_placeNotFound_throwsException() {
        org.mockito.Mockito.doThrow(new EntityNotFoundException("장소를 찾을 수 없습니다. id=999"))
                .when(stadiumAdminService).deletePlace(999L);

        assertThatThrownBy(() -> controller.deletePlace(999L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private StadiumAdminController.PlaceRequest createRequest() {
        StadiumAdminController.PlaceRequest request = new StadiumAdminController.PlaceRequest();
        request.setName("통밥");
        request.setCategory("food");
        request.setLat(37.5);
        request.setLng(127.0);
        return request;
    }

    private PlaceDto placeDto() {
        return PlaceDto.builder()
                .id(1L)
                .stadiumName("잠실야구장")
                .category("food")
                .name("통밥")
                .lat(37.5)
                .lng(127.0)
                .build();
    }
}
