package com.example.mypage.service;

import com.example.auth.entity.UserEntity;
import com.example.mypage.dto.UserProfileDto;
import com.example.kbo.entity.TeamEntity;
import com.example.auth.repository.UserRepository;
import com.example.kbo.repository.TeamRepository;
import com.example.kbo.util.TeamCodeNormalizer;
import com.example.profile.storage.service.ProfileImageService;

import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MyPageService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final ProfileImageService profileImageService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;

    // 이메일을 기반으로 사용자 프로필을 조회하여 DTO로 변환합니다.
    @Transactional(readOnly = true)
    public UserProfileDto getProfileByEmail(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        // Entity 데이터를 DTO로 매핑
        String teamId = user.getFavoriteTeam() != null ? user.getFavoriteTeam().getTeamId() : null;

        String profileImageUrl = profileImageService.getProfileImageUrlForUser(
                user.getId(),
                user.getProfileImageUrl());

        return Objects.requireNonNull(UserProfileDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .favoriteTeam(teamId)
                .profileImageUrl(profileImageUrl)
                .createdAt(user.getCreatedAt() != null
                        ? user.getCreatedAt().atZone(java.time.ZoneId.of("Asia/Seoul")).format(DATE_FORMATTER)
                        : null)
                .role(user.getRole())
                .cheerPoints(user.getCheerPoints())
                .build());
    }

    // 사용자 프로필 정보를 업데이트
    @Transactional
    public UserProfileDto updateProfile(String email, UserProfileDto updateDto) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        // 닉네임 업데이트
        user.setName(updateDto.getName());

        // 응원 구단 업데이트 (관리자는 null 허용)
        String newTeamId = updateDto.getFavoriteTeam();

        TeamEntity newTeam = null;
        if (newTeamId != null && !newTeamId.equals("없음") && !newTeamId.trim().isEmpty()) {
            String normalizedTeamId = TeamCodeNormalizer.normalize(newTeamId);
            newTeam = teamRepository.findByTeamIdAndIsActive(normalizedTeamId, true)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 팀 약어입니다: " + newTeamId));
        }
        user.setFavoriteTeam(newTeam);

        // Role은 변경하지 않음 (ADMIN/USER는 독립적으로 관리)

        // 프로필 이미지 URL 업데이트
        if (updateDto.getProfileImageUrl() != null) {
            String currentPath = profileImageService.normalizeProfileStoragePath(user.getProfileImageUrl());
            String requestedPath = profileImageService.normalizeProfileStoragePath(updateDto.getProfileImageUrl());
            if (Objects.equals(currentPath, requestedPath)) {
                user.setProfileImageUrl(requestedPath);
            } else if (Objects.equals(
                    profileImageService.getProfileImageUrlForUser(user.getId(), user.getProfileImageUrl()),
                    updateDto.getProfileImageUrl())) {
                user.setProfileImageUrl(user.getProfileImageUrl());
            } else {
                user.setProfileImageUrl(profileImageService.normalizeProfileStoragePathForUser(
                        user.getId(),
                        updateDto.getProfileImageUrl()));
            }
        }

        // 변경 사항을 DB에 저장
        userRepository.save(user);

        // 업데이트된 정보를 DTO로 다시 변환하여 반환
        return Objects.requireNonNull(getProfileByEmail(email));
    }

}
