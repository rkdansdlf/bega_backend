package com.example.auth.controller;

import com.example.auth.dto.PublicUserProfileDto;
import com.example.auth.service.UserService;
import com.example.common.dto.ApiResponse;
import com.example.common.exception.ForbiddenBusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    void getPublicUserProfileByHandle_decodesPercentEncodedHandlePath() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(userController).build();
        PublicUserProfileDto profile = PublicUserProfileDto.builder()
                .name("test")
                .handle("@user")
                .build();

        when(userService.getPublicUserProfileByHandle(anyString(), any())).thenReturn(profile);

        mockMvc.perform(get("/api/users/profile/%40User"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> handleCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> currentUserIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(userService).getPublicUserProfileByHandle(handleCaptor.capture(), currentUserIdCaptor.capture());
        assertThat(handleCaptor.getValue()).isNotBlank();
        assertThat(handleCaptor.getValue()).isIn("%40User", "User", "@User");
        assertThat(currentUserIdCaptor.getValue()).isNull();
    }

    @Test
    void checkSocialVerified_rejectsAnotherUsersStatusLookup() {
        assertThatThrownBy(() -> userController.checkSocialVerified(2L, 1L))
                .isInstanceOf(ForbiddenBusinessException.class)
                .hasMessageContaining("본인의 소셜 연동 상태만 조회할 수 있습니다.");
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
