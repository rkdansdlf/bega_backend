package com.example.mypage.service;

import com.example.auth.entity.UserEntity;
import com.example.mypage.dto.UserProfileDto;
import com.example.kbo.entity.TeamEntity;
import com.example.auth.repository.UserRepository;
import com.example.kbo.repository.TeamRepository;
import com.example.kbo.util.TeamCodeNormalizer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MyPageService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;

    // 이메일을 기반으로 사용자 프로필을 조회하여 DTO로 변환합니다.
    @Transactional(readOnly = true)
    public UserProfileDto getProfileByEmail(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        // Entity 데이터를 DTO로 매핑
        String teamId = user.getFavoriteTeam() != null ? user.getFavoriteTeam().getTeamId() : null;

        String profileImageUrl = repairProfileUrl(user.getProfileImageUrl());

        return UserProfileDto.builder()
                .name(user.getName())
                .email(user.getEmail())
                .favoriteTeam(teamId)
                .profileImageUrl(profileImageUrl)
                .createdAt(user.getCreatedAt() != null
                        ? user.getCreatedAt().atZone(java.time.ZoneId.of("Asia/Seoul")).format(DATE_FORMATTER)
                        : null)
                .role(user.getRole())
                .cheerPoints(user.getCheerPoints())
                .build();
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
            user.setProfileImageUrl(updateDto.getProfileImageUrl());
        }

        // 변경 사항을 DB에 저장
        userRepository.save(user);

        // 업데이트된 정보를 DTO로 다시 변환하여 반환
        return getProfileByEmail(email);
    }

    /**
     * Signed URL (전송량 초과로 402 오류 발생 시)을 Public URL로 변환하여 복구합니다.
     * 예: .../object/sign/profile-images/path?token=... ->
     * .../object/public/profile-images/path
     */
    private String repairProfileUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        // 이미 Public URL이거나 외부 URL인 경우 패스
        if (url.contains("/object/public/")) {
            return url;
        }

        // Signed URL 패턴 감지 (/object/sign/)
        if (url.contains("/object/sign/")) {
            // 토큰 파라미터 제거 (Query String 제거)
            String urlWithoutToken = url.split("\\?")[0];
            // /sign/을 /public/으로 치환
            return urlWithoutToken.replace("/object/sign/", "/object/public/");
        }

        return url;
    }
}
