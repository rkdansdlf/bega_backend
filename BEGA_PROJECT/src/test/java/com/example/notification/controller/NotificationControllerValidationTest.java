package com.example.notification.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.common.exception.GlobalExceptionHandler;
import com.example.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class NotificationControllerValidationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private NotificationService notificationService;
    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver(),
                        new PageableHandlerMethodArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(42L, null, "ROLE_USER"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        validator.destroy();
    }

    @Test
    void acceptsTheMaximumSnapshotPageSize() throws Exception {
        List<Long> notificationIds = LongStream.rangeClosed(1, 100).boxed().toList();
        when(notificationService.markAllAsRead(eq(42L), anyList())).thenReturn(notificationIds);

        mockMvc.perform(post("/api/notifications/mark-all-read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("notificationIds", notificationIds))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationIds[99]").value(100));

        verify(notificationService).markAllAsRead(42L, notificationIds);
    }

    @Test
    void rejectsSnapshotLargerThanTheMaximumBeforeCallingTheService() throws Exception {
        List<Long> notificationIds = LongStream.rangeClosed(1, 101).boxed().toList();

        mockMvc.perform(post("/api/notifications/mark-all-read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("notificationIds", notificationIds))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(notificationService);
    }

    @Test
    void rejectsNotificationPageSizeAboveTheMaximumBeforeCallingTheService() throws Exception {
        mockMvc.perform(get("/api/notifications/my").param("size", "1000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_PAGE_SIZE_EXCEEDED"));

        verifyNoInteractions(notificationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "101", "+101", "000101", "2147483648", "999999999999999999999999999999",
        "+2147483648", "00000000000000000000101"
    })
    void rejectsOverflowingNumericPageSizeBeforeCallingTheService(String size) throws Exception {
        mockMvc.perform(get("/api/notifications/my").param("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_PAGE_SIZE_EXCEEDED"));

        verifyNoInteractions(notificationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "+", "0", "-1", "999999999999999999x"})
    void preservesResolverFallbackForInvalidPageSize(String size) throws Exception {
        when(notificationService.getMyNotifications(eq(42L), any(Pageable.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/notifications/my").param("size", size))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).getMyNotifications(eq(42L), pageableCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(30);
    }

    @ParameterizedTest
    @ValueSource(strings = {"100", "+100", "000100"})
    void acceptsMaximumNotificationPageSize(String size) throws Exception {
        when(notificationService.getMyNotifications(eq(42L), any(Pageable.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/notifications/my").param("size", size))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).getMyNotifications(eq(42L), pageableCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void usesDefaultNotificationPageSizeWhenSizeIsOmitted() throws Exception {
        when(notificationService.getMyNotifications(eq(42L), any(Pageable.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/notifications/my"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).getMyNotifications(eq(42L), pageableCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(30);
    }
}
