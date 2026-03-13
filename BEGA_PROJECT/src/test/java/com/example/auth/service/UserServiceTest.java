package com.example.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.entity.UserEntity;
import com.example.auth.dto.PublicUserProfileDto;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.repository.UserBlockRepository;
import com.example.auth.repository.UserFollowRepository;
import com.example.auth.repository.RefreshRepository;
import com.example.mypage.dto.UserProfileDto;
import com.example.common.web.ClientIpResolver;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.repository.TeamRepository;
import com.example.mate.service.PartyService;
import com.example.profile.storage.service.ProfileImageService;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private RefreshRepository refreshRepository;

    @Mock
    private UserFollowRepository userFollowRepository;

    @Mock
    private UserBlockRepository userBlockRepository;

    @Mock
    private UserProviderRepository userProviderRepository;

    @Mock
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Mock
    private com.example.auth.util.JWTUtil jwtUtil;

    @Mock
    private PartyService partyService;

    @Mock
    private ProfileImageService profileImageService;

    @Mock
    private ClientIpResolver clientIpResolver;

    @Mock
    private AccountDeletionService accountDeletionService;

    @Mock
    private AccountSecurityService accountSecurityService;

    @Test
    void updateProfile_withNullProfileImageUrl_keepsExistingUrl() {
        UserEntity user = baseUser("https://cdn.example.com/old.png");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileDto updateDto = UserProfileDto.builder()
                .name("newName")
                .email("user@example.com")
                .favoriteTeam(null)
                .bio("hi")
                .profileImageUrl(null)
                .build();

        UserEntity result = userService.updateProfile(1L, updateDto);

        assertEquals("https://cdn.example.com/old.png", result.getProfileImageUrl());
        assertEquals("newName", result.getName());
        verify(userRepository).save(user);
    }

    @Test
    void updateProfile_withBlankProfileImageUrl_keepsExistingUrl() {
        UserEntity user = baseUser("https://cdn.example.com/old.png");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileDto updateDto = UserProfileDto.builder()
                .name("newName")
                .email("user@example.com")
                .favoriteTeam(null)
                .bio("hi")
                .profileImageUrl("")
                .build();

        UserEntity result = userService.updateProfile(1L, updateDto);

        assertEquals("https://cdn.example.com/old.png", result.getProfileImageUrl());
        assertEquals("newName", result.getName());
        verify(userRepository).save(user);
    }

    @Test
    void updateProfile_withNewProfileImageUrl_updatesUrl() {
        UserEntity user = baseUser("https://cdn.example.com/old.png");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileDto updateDto = UserProfileDto.builder()
                .name("newName")
                .email("user@example.com")
                .favoriteTeam(null)
                .bio("hi")
                .profileImageUrl("https://cdn.example.com/new.png")
                .build();

        UserEntity result = userService.updateProfile(1L, updateDto);

        assertEquals("https://cdn.example.com/new.png", result.getProfileImageUrl());
        assertEquals("newName", result.getName());
        verify(userRepository).save(user);
    }

    @Test
    void getPublicUserProfileByHandle_resolvesProfileImageUrl() {
        UserEntity user = baseUser("https://cdn.example.com/old.png");
        when(userRepository.findByHandle("@user")).thenReturn(Optional.of(user));
        when(profileImageService.getProfileImageUrl("https://cdn.example.com/old.png"))
                .thenReturn("https://cdn.example.com/old.png?v=resolved");

        PublicUserProfileDto result = userService.getPublicUserProfileByHandle("@user", null);

        assertEquals("@user", result.getHandle());
        assertEquals("https://cdn.example.com/old.png?v=resolved", result.getProfileImageUrl());
        verify(profileImageService).getProfileImageUrl("https://cdn.example.com/old.png");
    }

    @Test
    void getPublicUserProfileByHandle_withMissingProfileImage_returnsNullWithoutResolver() {
        UserEntity user = baseUser(null);
        when(userRepository.findByHandle("@user")).thenReturn(Optional.of(user));

        PublicUserProfileDto result = userService.getPublicUserProfileByHandle("@user", null);

        assertEquals(null, result.getProfileImageUrl());
        verify(profileImageService, never()).getProfileImageUrl(any());
    }

    @Test
    void getPublicUserProfileByHandle_privateAccountRequiresFollower() {
        UserEntity user = baseUser("https://cdn.example.com/private.png");
        user.setPrivateAccount(true);
        when(userRepository.findByHandle("@user")).thenReturn(Optional.of(user));

        assertThrows(AccessDeniedException.class, () -> userService.getPublicUserProfileByHandle("@user", null));
    }

    @Test
    void getPublicUserProfileByHandle_privateAccountAllowsFollower() {
        UserEntity user = baseUser("https://cdn.example.com/private.png");
        user.setPrivateAccount(true);
        when(userRepository.findByHandle("@user")).thenReturn(Optional.of(user));
        when(userFollowRepository.existsById(any())).thenReturn(true);

        PublicUserProfileDto result = userService.getPublicUserProfileByHandle("@user", 99L);

        assertEquals("@user", result.getHandle());
        verify(userFollowRepository).existsById(any());
    }

    @Test
    void changePassword_invalidatesExistingSessions() {
        UserEntity user = baseUser("https://cdn.example.com/old.png");
        user.setPassword("encoded-old-password");
        user.setTokenVersion(2);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(bCryptPasswordEncoder.matches("current-password", "encoded-old-password")).thenReturn(true);
        when(bCryptPasswordEncoder.encode("new-password")).thenReturn("encoded-new-password");

        userService.changePassword(1L, "current-password", "new-password");

        assertEquals("encoded-new-password", user.getPassword());
        assertEquals(3, user.getTokenVersion());
        verify(userRepository).save(user);
        verify(refreshRepository).deleteByEmail("user@example.com");
    }

    @Test
    void checkAndApplyDailyLoginBonus_awardsPointsOnlyOncePerDay() {
        UserEntity user = baseUser(null);
        user.setCheerPoints(10);

        int firstLoginPoints = userService.checkAndApplyDailyLoginBonus(user);
        int secondLoginPoints = userService.checkAndApplyDailyLoginBonus(user);

        assertEquals(15, firstLoginPoints);
        assertEquals(15, secondLoginPoints);
        assertEquals(LocalDate.now(), user.getLastBonusDate());
        verify(userRepository, times(2)).save(user);
    }

    @Test
    void checkAndApplyDailyLoginBonus_preservesExistingPointsWhenAlreadyAwardedToday() {
        UserEntity user = baseUser(null);
        user.setCheerPoints(7);
        user.setLastBonusDate(LocalDate.now());

        int currentPoints = userService.checkAndApplyDailyLoginBonus(user);

        assertEquals(7, currentPoints);
        assertEquals(LocalDate.now(), user.getLastBonusDate());
        verify(userRepository).save(user);
    }

    private UserEntity baseUser(String profileImageUrl) {
        TeamEntity defaultTeam = TeamEntity.builder()
                .teamId("doosan")
                .teamName("두산 베어스")
                .teamShortName("두산")
                .city("서울")
                .stadiumName("잠실야구장")
                .foundedYear(1)
                .isActive(true)
                .build();

        return UserEntity.builder()
                .id(1L)
                .uniqueId(UUID.randomUUID())
                .name("originalName")
                .handle("@user")
                .email("user@example.com")
                .profileImageUrl(profileImageUrl)
                .role("ROLE_USER")
                .favoriteTeam(defaultTeam)
                .bio("old bio")
                .build();
    }
}
