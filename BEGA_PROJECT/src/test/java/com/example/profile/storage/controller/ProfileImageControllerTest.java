package com.example.profile.storage.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.profile.storage.service.ProfileImageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ProfileImageControllerTest {

    @Mock
    private ProfileImageService profileImageService;

    @Test
    void uploadProfileImageDoesNotLogClientSuppliedFilename() {
        String filename = "private-controller-filename-sentinel.png";
        MockMultipartFile file = new MockMultipartFile("file", filename, "image/png", new byte[] {1});
        when(profileImageService.uploadProfileImage(9L, file)).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(9L, "test"));

        Logger logger = (Logger) LoggerFactory.getLogger(ProfileImageController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            var response = new ProfileImageController(profileImageService).uploadProfileImage(file);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains(filename));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            SecurityContextHolder.clearContext();
        }
    }
}
