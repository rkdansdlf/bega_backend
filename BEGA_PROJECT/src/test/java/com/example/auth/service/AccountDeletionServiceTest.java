package com.example.auth.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.entity.AccountDeletionToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.AccountDeletionTokenRepository;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.UserRepository;
import com.example.mate.service.PartyService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    @InjectMocks
    private AccountDeletionService accountDeletionService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshRepository refreshRepository;

    @Mock
    private AccountDeletionTokenRepository accountDeletionTokenRepository;

    @Mock
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private AccountSecurityService accountSecurityService;

    @Mock
    private PartyService partyService;

    @Test
    void scheduleAccountDeletion_sendsRecoveryEmailWithAccountSettingsRedirect() {
        UserEntity user = UserEntity.builder()
                .id(41L)
                .uniqueId(UUID.randomUUID())
                .email("user@example.com")
                .role("ROLE_USER")
                .name("user")
                .handle("@user")
                .password("encoded-password")
                .provider("LOCAL")
                .build();

        when(userRepository.findById(41L)).thenReturn(Optional.of(user));
        when(bCryptPasswordEncoder.matches("Password1!", "encoded-password")).thenReturn(true);
        when(accountDeletionTokenRepository.save(any(AccountDeletionToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        accountDeletionService.scheduleAccountDeletion(41L, "Password1!");

        verify(refreshRepository).deleteByEmail("user@example.com");
        verify(accountDeletionTokenRepository).deleteByUser_Id(41L);
        verify(emailService).sendAccountDeletionRecoveryEmail(
                eq("user@example.com"),
                anyString(),
                any(),
                eq("/mypage?view=accountSettings"));
        verify(accountSecurityService).recordAccountDeletionScheduled(anyLong(), any());
    }
}
