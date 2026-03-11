package com.example.auth.controller;

import com.example.auth.dto.PublicUserProfileDto;
import com.example.auth.service.UserService;
import com.example.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @Test
    void getPublicUserProfileByHandle_returnsProfileImageUrl() {
        PublicUserProfileDto profile = PublicUserProfileDto.builder()
                .name("test")
                .handle("@user")
                .favoriteTeam("SS")
                .profileImageUrl("https://cdn.example.com/avatar.png?v=resolved")
                .bio("hello")
                .cheerPoints(12)
                .build();

        when(userService.getPublicUserProfileByHandle("@user", null)).thenReturn(profile);

        ResponseEntity<ApiResponse> result = userController.getPublicUserProfile("@user", null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isInstanceOf(ApiResponse.class);
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getMessage()).isEqualTo("사용자 프로필 조회 성공");
        assertThat(result.getBody().getData()).isInstanceOf(PublicUserProfileDto.class);
        PublicUserProfileDto actual = (PublicUserProfileDto) result.getBody().getData();
        assertThat(actual.getProfileImageUrl()).isEqualTo("https://cdn.example.com/avatar.png?v=resolved");
    }

    @Test
    void checkSocialVerified_rejectsAnotherUsersStatusLookup() {
        ResponseEntity<ApiResponse> result = userController.checkSocialVerified(2L, 1L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(result.getBody()).isInstanceOf(ApiResponse.class);
        assertThat(result.getBody().isSuccess()).isFalse();
        assertThat(result.getBody().getMessage()).isEqualTo("본인의 소셜 연동 상태만 조회할 수 있습니다.");
    }

    @Test
    void checkSocialVerified_returnsOwnStatus() {
        when(userService.isSocialVerified(2L)).thenReturn(true);

        ResponseEntity<ApiResponse> result = userController.checkSocialVerified(2L, 2L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isInstanceOf(ApiResponse.class);
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getData()).isEqualTo(true);
    }
}
