package com.example.cheerboard.storage.controller;

import com.example.cheerboard.storage.dto.SignedUrlDto;
import com.example.cheerboard.storage.service.ImageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageControllerTest {

    @Mock
    private ImageService imageService;

    @InjectMocks
    private ImageController imageController;

    @Test
    void renewSignedUrl_returnsForbiddenWhenAccessDenied() {
        when(imageService.renewSignedUrl(10L)).thenThrow(new AccessDeniedException("forbidden"));

        var response = imageController.renewSignedUrl(10L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNull();
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
