package com.example.demo.mypage.service;

import com.example.demo.entity.UserEntity;
import com.example.demo.mypage.dto.UserProfileDto;
import com.example.demo.entity.TeamEntity; 
import com.example.demo.repo.UserRepository;
import com.example.demo.repo.TeamRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger; // Logger import 추가
import org.slf4j.LoggerFactory; // LoggerFactory import 추가

import java.time.format.DateTimeFormatter;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MyPageService {

    private static final Logger log = LoggerFactory.getLogger(MyPageService.class); // Logger 인스턴스 추가

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;
    
    // ** 헬퍼 메서드: Team ID(약어)를 한글 팀 이름으로 변환 (GET 요청 시 필요) **
    private String getTeamNameById(String teamId) {
        if (teamId == null) {
            return "없음";
        }
        return switch (teamId) {
            case "SS" -> "삼성 라이온즈";
            case "LT" -> "롯데 자이언츠";
            case "LG" -> "LG 트윈스";
            case "OB" -> "두산 베어스";
            case "WO" -> "키움 히어로즈";
            case "HH" -> "한화 이글스";
            case "SK" -> "SSG 랜더스";
            case "NC" -> "NC 다이노스";
            case "KT" -> "KT 위즈";
            case "HT" -> "기아 타이거즈";
            default -> "없음";
        };
    }
    
    
    // ⭐ 새로운 헬퍼 메서드: Team ID(약어)에 따라 Role Key를 결정 ⭐
    private String getRoleKeyByTeamId(String teamId) {
        if (teamId == null || "없음".equals(teamId) || teamId.trim().isEmpty()) {
            return "ROLE_USER"; 
        }

        // ROLE_ID 형태로 Role Key를 생성합니다. (대문자 사용)
        return "ROLE_" + teamId.toUpperCase();
    }
    
//     * [READ] 이메일을 기반으로 사용자 프로필을 조회하여 DTO로 변환합니다.
    @Transactional(readOnly = true)
    public UserProfileDto getProfileByEmail(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        // Entity 데이터를 DTO로 매핑
        String teamId = user.getFavoriteTeam() != null ? user.getFavoriteTeam().getTeamId() : null;
        
        // DTO 반환 시에는 프론트엔드가 요구하는 한글 이름과 현재 Role을 포함합니다.
        return UserProfileDto.builder()
                .name(user.getName())
                .email(user.getEmail())
                .favoriteTeam(teamId) // ⭐ DTO에는 약칭을 담아 반환합니다. (프론트에서 LG를 받도록 약속했기 때문)
                .profileImageUrl(user.getProfileImageUrl())
                .createdAt(user.getCreatedAt().format(DATE_FORMATTER)) 
                .role(user.getRole()) // ⭐ 현재 Role 필드도 포함
                .build();
    }

//     * [UPDATE] 사용자 프로필 정보를 업데이트합니다.
    @Transactional
    public UserProfileDto updateProfile(String email, UserProfileDto updateDto) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));
        
        // 1. 닉네임 업데이트
        user.setName(updateDto.getName()); 
        // 2. 응원 구단 업데이트 (약칭을 Team ID로 사용)
        String newTeamId = updateDto.getFavoriteTeam(); 
        
        TeamEntity newTeam = null;
        if (newTeamId != null && !newTeamId.equals("없음")) {
            // 새 팀 ID(약칭)로 TeamEntity 조회
        	newTeam = teamRepository.findByTeamId(newTeamId) 
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 팀 약어입니다: " + newTeamId));
        }
        user.setFavoriteTeam(newTeam); // TeamEntity 객체 설정 (null일 수 있음)
        
        // 3. 권한 (Role) 업데이트 로직
        String newRoleKey = getRoleKeyByTeamId(newTeamId);
        user.setRole(newRoleKey); 
        
        // 4. 프로필 이미지 URL 업데이트 
        if (updateDto.getProfileImageUrl() != null) {
            user.setProfileImageUrl(updateDto.getProfileImageUrl());
        }
        
        // ⭐ DEBUG 로그 추가: DB에 반영되기 직전 Entity가 가진 Role 값을 확인 ⭐
        log.info("DEBUG-ROLE-CHECK: Entity Role set to: {}", user.getRole());
        
        // save를 호출하여 변경 사항을 DB에 반영
        userRepository.save(user);

        // 업데이트된 정보를 DTO로 다시 변환하여 반환
        return getProfileByEmail(email);
    }
}
