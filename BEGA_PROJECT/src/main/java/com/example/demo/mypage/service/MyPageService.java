package com.example.demo.mypage.service;

import com.example.demo.entity.UserEntity;
import com.example.demo.mypage.dto.UserProfileDto;
import com.example.demo.entity.TeamEntity;
import com.example.demo.repo.UserRepository;
import com.example.demo.repo.TeamRepository;

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

    // Team ID에 따라 Role Key 설정
    private String getRoleKeyByTeamId(String teamId) {
        if (teamId == null || "없음".equals(teamId) || teamId.trim().isEmpty()) {
            return "ROLE_USER";
        }

        // ROLE_ID 형태로 Role Key를 생성합니다
        return "ROLE_" + teamId.toUpperCase();
    }

    // 이메일을 기반으로 사용자 프로필을 조회하여 DTO로 변환합니다.
    @Transactional(readOnly = true)
    public UserProfileDto getProfileByEmail(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        // Entity 데이터를 DTO로 매핑
        String teamId = user.getFavoriteTeam() != null ? user.getFavoriteTeam().getTeamId() : null;

        return UserProfileDto.builder()
                .name(user.getName())
                .email(user.getEmail())
                .favoriteTeam(teamId)
                .profileImageUrl(user.getProfileImageUrl())
                .createdAt(user.getCreatedAt().format(DATE_FORMATTER))
                .role(user.getRole())
                .build();
    }

    // 사용자 프로필 정보를 업데이트
    @Transactional
    public UserProfileDto updateProfile(String email, UserProfileDto updateDto) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        // 닉네임 업데이트
        user.setName(updateDto.getName());
        // 응원 구단 업데이트
        String newTeamId = updateDto.getFavoriteTeam();

        TeamEntity newTeam = null;
        if (newTeamId != null && !newTeamId.equals("없음")) {
            // 새로 적용한 Team ID를 TeamEntity 조회
            newTeam = teamRepository.findByTeamId(newTeamId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 팀 약어입니다: " + newTeamId));
        }
        user.setFavoriteTeam(newTeam); // TeamEntity 객체 설정

        // 권한 (Role) 업데이트
        String newRoleKey = getRoleKeyByTeamId(newTeamId);
        user.setRole(newRoleKey);

        // 프로필 이미지 URL 업데이트
        if (updateDto.getProfileImageUrl() != null) {
            user.setProfileImageUrl(updateDto.getProfileImageUrl());
        }

        // 변경 사항을 DB에 저장
        userRepository.save(user);

        // 업데이트된 정보를 DTO로 다시 변환하여 반환
        return getProfileByEmail(email);
    }
}
