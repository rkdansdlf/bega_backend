package com.example.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.entity.UserEntity;
import com.example.auth.dto.PublicUserProfileDto;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.repository.RefreshRepository;
import com.example.mypage.dto.UserProfileDto;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.repository.TeamRepository;
import com.example.mate.service.PartyService;
import com.example.profile.storage.service.ProfileImageService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    private UserProviderRepository userProviderRepository;

    @Mock
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Mock
    private com.example.auth.util.JWTUtil jwtUtil;

    @Mock
    private PartyService partyService;

    @Mock
    private ProfileImageService profileImageService;

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

        PublicUserProfileDto result = userService.getPublicUserProfileByHandle("@user");

        assertEquals("@user", result.getHandle());
        assertEquals("https://cdn.example.com/old.png?v=resolved", result.getProfileImageUrl());
        verify(profileImageService).getProfileImageUrl("https://cdn.example.com/old.png");
    }

    @Test
    void getPublicUserProfileById_resolvesProfileImageUrl() {
        UserEntity user = baseUser("https://cdn.example.com/by-id.png");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(profileImageService.getProfileImageUrl("https://cdn.example.com/by-id.png"))
                .thenReturn("https://cdn.example.com/by-id.png?v=resolved");

        PublicUserProfileDto result = userService.getPublicUserProfile(1L);

        assertEquals(1L, result.getId());
        assertEquals("https://cdn.example.com/by-id.png?v=resolved", result.getProfileImageUrl());
        verify(profileImageService).getProfileImageUrl("https://cdn.example.com/by-id.png");
    }

    @Test
    void getPublicUserProfileByHandle_withMissingProfileImage_returnsNullWithoutResolver() {
        UserEntity user = baseUser(null);
        when(userRepository.findByHandle("@user")).thenReturn(Optional.of(user));

        PublicUserProfileDto result = userService.getPublicUserProfileByHandle("@user");

        assertEquals(null, result.getProfileImageUrl());
        verify(profileImageService, never()).getProfileImageUrl(any());
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
