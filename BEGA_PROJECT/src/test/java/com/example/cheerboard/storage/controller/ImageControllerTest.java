package com.example.cheerboard.storage.controller;

import com.example.common.exception.GlobalExceptionHandler;
import com.example.cheerboard.storage.dto.SignedUrlDto;
import com.example.cheerboard.storage.service.ImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ImageControllerTest {

    @Mock
    private ImageService imageService;

    @InjectMocks
    private ImageController imageController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(imageController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void renewSignedUrl_returnsForbiddenWhenAccessDenied() throws Exception {
        when(imageService.renewSignedUrl(10L)).thenThrow(new AccessDeniedException("forbidden"));

        mockMvc.perform(post("/api/images/10/signed-url")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("forbidden"));
    }

    @Test
    void renewSignedUrl_returnsOkWhenAuthorized() {
        SignedUrlDto dto = new SignedUrlDto("https://signed.example/test", Instant.parse("2026-03-10T12:00:00Z"));
        when(imageService.renewSignedUrl(10L)).thenReturn(dto);

        var response = imageController.renewSignedUrl(10L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(dto);
    }
}
