package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.entity.AccountSecurityEvent;
import com.example.auth.entity.TrustedDevice;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.AccountSecurityEventRepository;
import com.example.auth.repository.TrustedDeviceRepository;
import com.example.common.web.ClientIpResolver;
import com.example.notification.entity.Notification;
import com.example.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class AccountSecurityServiceTest {

    private static final String IPHONE_SAFARI_USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_3 like Mac OS X) AppleWebKit/605.1.15 "
                    + "(KHTML, like Gecko) Version/17.3 Mobile/15E148 Safari/604.1";

    @Mock
    private AccountSecurityEventRepository accountSecurityEventRepository;

    @Mock
    private TrustedDeviceRepository trustedDeviceRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private EmailService emailService;

    @Mock
    private ClientIpResolver clientIpResolver;

    private AccountSecurityService accountSecurityService;

    @BeforeEach
    void setUp() {
        accountSecurityService = new AccountSecurityService(
                accountSecurityEventRepository,
                trustedDeviceRepository,
                notificationService,
                emailService,
                clientIpResolver);
    }

    @Test
    void handleSuccessfulLogin_reactivatesRevokedTrustedDeviceAndRecordsNewDeviceLogin() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@example.com");

        TrustedDevice revokedTrustedDevice = TrustedDevice.builder()
                .id(15L)
                .userId(1L)
                .fingerprint("existing-fingerprint")
                .deviceLabel("Old iPhone")
                .deviceType("mobile")
                .browser("Safari")
                .os("iOS")
                .firstSeenAt(LocalDateTime.now().minusDays(30))
                .lastSeenAt(LocalDateTime.now().minusDays(2))
                .lastLoginAt(LocalDateTime.now().minusDays(2))
                .lastIp("198.51.100.10")
                .revokedAt(LocalDateTime.now().minusHours(1))
                .build();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", IPHONE_SAFARI_USER_AGENT);
        request.setRemoteAddr("198.51.100.20");

        when(clientIpResolver.resolveOrUnknown(request)).thenReturn("198.51.100.20");
        when(trustedDeviceRepository.findByUserIdAndFingerprintAndRevokedAtIsNull(eq(1L), anyString()))
                .thenReturn(Optional.empty());
        when(trustedDeviceRepository.findByUserIdAndFingerprint(eq(1L), anyString()))
                .thenReturn(Optional.of(revokedTrustedDevice));

        accountSecurityService.handleSuccessfulLogin(user, request);

        assertThat(revokedTrustedDevice.getRevokedAt()).isNull();
        assertThat(revokedTrustedDevice.getDeviceLabel()).isEqualTo("iPhone");
        assertThat(revokedTrustedDevice.getDeviceType()).isEqualTo("mobile");
        assertThat(revokedTrustedDevice.getBrowser()).isEqualTo("Safari");
        assertThat(revokedTrustedDevice.getOs()).isEqualTo("iOS");
        assertThat(revokedTrustedDevice.getLastIp()).isEqualTo("198.51.100.20");

        verify(trustedDeviceRepository).save(revokedTrustedDevice);

        ArgumentCaptor<AccountSecurityEvent> eventCaptor = ArgumentCaptor.forClass(AccountSecurityEvent.class);
        verify(accountSecurityEventRepository, times(2)).save(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(AccountSecurityEvent::getEventType)
                .containsExactlyInAnyOrder(
                        AccountSecurityEvent.EventType.LOGIN_SUCCESS,
                        AccountSecurityEvent.EventType.NEW_DEVICE_LOGIN);

        verify(notificationService).createNotification(
                eq(1L),
                eq(Notification.NotificationType.NEW_DEVICE_LOGIN),
                eq("새 기기 로그인 감지"),
                contains("iPhone"),
                isNull());
        verify(emailService).sendNewDeviceLoginEmail(
                "user@example.com",
                "iPhone",
                "Safari",
                "iOS",
                "198.51.100.20");
    }
}
