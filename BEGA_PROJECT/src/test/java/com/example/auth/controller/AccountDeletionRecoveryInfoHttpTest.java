package com.example.auth.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auth.dto.AccountDeletionRecoveryInfoDto;
import com.example.auth.service.AccountDeletionService;
import com.example.auth.service.AccountSecurityService;
import com.example.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AccountDeletionRecoveryInfoHttpTest {
    private AccountDeletionService deletionService;
    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        deletionService = mock(AccountDeletionService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AccountSecurityController(mock(AccountSecurityService.class), deletionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    void readsRecoveryInfoFromJsonBody() throws Exception {
        when(deletionService.getRecoveryInfo("synthetic-token"))
                .thenReturn(new AccountDeletionRecoveryInfoDto("2099-12-31T09:00:00"));

        mockMvc.perform(post("/api/auth/account/deletion/recovery/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"synthetic-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scheduledFor").value("2099-12-31T09:00:00"));
        verify(deletionService).getRecoveryInfo("synthetic-token");
    }

    @Test
    void rejectsBlankTokenBeforeService() throws Exception {
        mockMvc.perform(post("/api/auth/account/deletion/recovery/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\" \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(deletionService);
    }

    @Test
    void oldQueryBasedGetDoesNotReadRecoveryInfo() throws Exception {
        mockMvc.perform(get("/api/auth/account/deletion/recovery").param("token", "synthetic-token"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400));
        verifyNoInteractions(deletionService);
    }
}
