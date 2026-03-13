package com.example.BegaDiary.Controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Service.BegaDiaryService;
import com.example.BegaDiary.Service.BegaGameService;
import com.example.BegaDiary.Service.SeatViewService;
import com.example.auth.entity.UserEntity;
import com.example.cheerboard.storage.service.ImageService;
import com.example.common.exception.GlobalExceptionHandler;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class DiaryControllerTest {

    @Mock
    private BegaDiaryService diaryService;

    @Mock
    private BegaGameService gameService;

    @Mock
    private ImageService imageService;

    @Mock
    private SeatViewService seatViewService;

    @InjectMocks
    private DiaryController diaryController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(diaryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("인증 없이 다이어리 저장을 호출하면 표준 401 응답을 반환한다")
    void saveDiary_requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/diary/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));

        verifyNoInteractions(diaryService);
    }

    @Test
    @DisplayName("예상 밖 이미지 업로드 실패는 표준 IMAGE_PROCESSING_ERROR 응답으로 감싼다")
    void uploadImages_wrapsUnexpectedFailures() throws Exception {
        when(diaryService.getDiaryEntityById(1L)).thenReturn(ownedDiary(1L));
        when(imageService.uploadDiaryImages(eq(1L), eq(1L), anyList()))
                .thenReturn(Mono.error(new RuntimeException("upload failed")));

        mockMvc.perform(multipart("/api/diary/{id}/images", 1L)
                        .file(imageFile())
                        .principal(() -> "1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("IMAGE_PROCESSING_ERROR"))
                .andExpect(jsonPath("$.message").value("이미지 처리 중 오류가 발생했습니다: upload failed"));

        verifyNoInteractions(seatViewService);
    }

    @Test
    @DisplayName("잘못된 sourceTypes 값은 표준 400 응답을 반환한다")
    void uploadImages_rejectsInvalidSourceTypes() throws Exception {
        when(diaryService.getDiaryEntityById(1L)).thenReturn(ownedDiary(1L));
        when(imageService.uploadDiaryImages(eq(1L), eq(1L), anyList()))
                .thenReturn(Mono.just(List.of("diary/1/1/test.webp")));

        mockMvc.perform(multipart("/api/diary/{id}/images", 1L)
                        .file(imageFile())
                        .param("sourceTypes", "invalid")
                        .principal(() -> "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_SEAT_VIEW_SOURCE_TYPE"))
                .andExpect(jsonPath("$.message").value("sourceTypes 값이 올바르지 않습니다: invalid"));

        verifyNoInteractions(seatViewService);
    }

    private BegaDiary ownedDiary(Long userId) {
        return BegaDiary.builder()
                .user(UserEntity.builder().id(userId).build())
                .build();
    }

    private MockMultipartFile imageFile() {
        return new MockMultipartFile("images", "seat.webp", "image/webp", "image".getBytes());
    }
}
