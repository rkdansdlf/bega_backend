package com.example.demo.service;

import java.util.Map;
import java.util.Optional;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.demo.dto.UserDto;
import com.example.demo.dto.SignupDto; 
import com.example.demo.mypage.dto.UserProfileDto;
import com.example.demo.entity.UserEntity;
import com.example.demo.entity.TeamEntity; 
import com.example.demo.entity.Role;
import com.example.demo.jwt.JWTUtil;
import com.example.demo.repo.UserRepository;
import com.example.demo.repo.TeamRepository;

import com.example.demo.exception.UserNotFoundException;
import com.example.demo.exception.TeamNotFoundException;
import com.example.demo.exception.DuplicateEmailException;
import com.example.demo.exception.InvalidCredentialsException;
import com.example.demo.exception.SocialLoginRequiredException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class); 
    private static final long ACCESS_EXPIRATION_TIME = 1000L * 60 * 60;

    private final UserRepository userRepository;
    private final TeamRepository teamRepository; 
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final JWTUtil jwtUtil;
  
    /**
     * 회원가입
     */
    @Transactional
    public UserEntity saveUser(SignupDto signupDto) {
        UserDto userDto = signupDto.toUserDto();
        this.signUp(userDto);
        
        return findUserByEmailOrThrow(userDto.getEmail());
    }

    /**
     * 회원가입 메인 로직
     */
    @Transactional
    public void signUp(UserDto userDto) {
        log.info("--- [SignUp] Attempt - Email: {} ---", userDto.getEmail());

        Optional<UserEntity> existingUser = userRepository.findByEmail(userDto.getEmail());

        if (existingUser.isPresent()) {
            handleExistingUser(existingUser.get(), userDto);
            return;
        }

        createNewUser(userDto);
    }

    /**
     * 기존 사용자 처리 (중복 체크 및 소셜 연동)
     */
    private void handleExistingUser(UserEntity existingUser, UserDto userDto) {
        log.info("Existing User Found - ID: {}, Provider: {}", 
            existingUser.getId(), existingUser.getProvider());

        boolean isLocalSignup = isLocalSignupAttempt(userDto);
        
        if (isLocalSignup) {
            handleLocalSignupConflict(existingUser);
            return;
        }
        
        if (userDto.getProviderId() != null) {
            handleSocialLinking(existingUser, userDto);
        }
    }

    /**
     * 로컬 회원가입 충돌 처리
     */
    private void handleLocalSignupConflict(UserEntity existingUser) {
        if (existingUser.isOAuth2User()) {
            log.warn("Attempted Local Signup with existing Social Account");
            throw new SocialLoginRequiredException();
        } else {
            log.warn("Attempted Local Signup with existing Local Account");
            throw new DuplicateEmailException(existingUser.getEmail());
        }
    }

    /**
     * 소셜 계정 연동 처리
     */
    private void handleSocialLinking(UserEntity existingUser, UserDto userDto) {
        if (existingUser.getProvider() == null || "LOCAL".equals(existingUser.getProvider())) {
            existingUser.setProvider(userDto.getProvider());
            existingUser.setProviderId(userDto.getProviderId());
            userRepository.save(existingUser);
        }
    }

    /**
     * 신규 사용자 생성
     */
    private void createNewUser(UserDto userDto) {

        String favoriteTeamName = userDto.getFavoriteTeam();
        String roleKey = getRoleKeyByFavoriteTeam(favoriteTeamName);
        String teamId = getTeamIdByFavoriteTeamName(favoriteTeamName);
        TeamEntity team = findTeamById(teamId);
        String encodedPassword = encodePasswordIfPresent(userDto.getPassword());

        UserEntity user = UserEntity.builder()
            .name(userDto.getName()) 
            .email(userDto.getEmail())
            .password(encodedPassword) 
            .favoriteTeam(team) 
            .role(roleKey)             
            .provider(userDto.getProvider() != null ? userDto.getProvider() : "LOCAL")
            .providerId(userDto.getProviderId())
            .build();

        userRepository.save(user);
    }


    /**
     * 로그인 인증 및 JWT 토큰 생성
     */
    @Transactional(readOnly = true)
    public Map<String, Object> authenticateAndGetToken(String email, String password) {
        UserEntity user = findUserByEmailOrThrow(email);
        
        validatePassword(user, password);

        String accessToken = jwtUtil.createJwt(
            user.getEmail(),
            user.getRole(),
            user.getId(),
            ACCESS_EXPIRATION_TIME
        );
        
        return Map.of(
            "accessToken", accessToken, 
            "name", user.getName(),
            "role", user.getRole()
        );
    }


    /**
     * 프로필 업데이트
     */
    @Transactional
    public UserEntity updateProfile(Long id, UserProfileDto updateDto) {
        UserEntity user = findUserById(id);

        updateUserName(user, updateDto.getName());
        updateProfileImage(user, updateDto.getProfileImageUrl());
        updateFavoriteTeam(user, updateDto.getFavoriteTeam());

        return userRepository.save(user);
    }

    private void updateUserName(UserEntity user, String name) {
        user.setName(name);
    }

    private void updateProfileImage(UserEntity user, String imageUrl) {
        if (imageUrl != null && !imageUrl.isEmpty()) {
            user.setProfileImageUrl(imageUrl);
        }
    }

    /**
     * 응원팀 업데이트
     */
    private void updateFavoriteTeam(UserEntity user, String teamId) {
        if (teamId != null && !teamId.trim().isEmpty()) {
            TeamEntity team = teamRepository.findById(teamId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));
            user.setFavoriteTeam(team);
        } else {
            user.setFavoriteTeam(null);
        }
        
        String roleKey = getRoleKeyByTeamId(teamId);
        user.setRole(roleKey);
    }


    /**
     * ID로 사용자 조회
     */
    @Transactional(readOnly = true)
    public UserEntity findUserById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException(id));  
    }

    /**
     * 이메일 중복 체크
     */
    @Transactional(readOnly = true)
    public boolean isEmailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * 이메일로 사용자 ID 조회
     */
    @Transactional(readOnly = true)
    public Long getUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
            .map(UserEntity::getId)
            .orElseThrow(() -> new UserNotFoundException("email", email));  
    }

    /**
     * 이메일로 사용자 DTO 조회
     */
    @Transactional(readOnly = true)
    public UserDto findUserByEmail(String email) {
        return userRepository.findByEmail(email)
            .map(this::convertToUserDto)
            .orElseThrow(() -> new UserNotFoundException("email", email));  
    }

    /**
     * Private 헬퍼 메서드
     */
    private boolean isLocalSignupAttempt(UserDto userDto) {
        return userDto.getProvider() == null || "LOCAL".equals(userDto.getProvider());
    }


    /**
     * 비밀번호 검증
     */
    private void validatePassword(UserEntity user, String password) {
        if (user.getPassword() == null) {
            throw new SocialLoginRequiredException();  
        }
        
        if (!bCryptPasswordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException();  
        }
    }

    /**
     * 이메일로 사용자 조회 (로그인용)
     */
    private UserEntity findUserByEmailOrThrow(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new InvalidCredentialsException());  
    }

    /**
     * 팀 ID로 팀 조회
     */
    private TeamEntity findTeamById(String teamId) {
        if (teamId == null) {
            return null;
        }
        
        log.info("Fetching TeamEntity with ID: {}", teamId);
        return teamRepository.findById(teamId)
            .orElseThrow(() -> new TeamNotFoundException(teamId));  
    }

    private String encodePasswordIfPresent(String password) {
        return password != null ? bCryptPasswordEncoder.encode(password) : null;
    }

    private UserDto convertToUserDto(UserEntity userEntity) {
        return UserDto.builder()
            .id(userEntity.getId())
            .name(userEntity.getName()) 
            .email(userEntity.getEmail())
            .favoriteTeam(userEntity.getFavoriteTeamId()) 
            .role(userEntity.getRole())
            .provider(userEntity.getProvider())
            .providerId(userEntity.getProviderId())
            .build();
    }

    /**
     * Role 및 Team 매핑 메서드
     */
    private String getRoleKeyByFavoriteTeam(String teamName) {
        if (teamName == null || "없음".equals(teamName) || teamName.trim().isEmpty()) {
            return Role.USER.getKey();
        }

        Role role = switch (teamName) {
            case "삼성 라이온즈" -> Role.Role_SS;
            case "롯데 자이언츠" -> Role.Role_LT;
            case "LG 트윈스" -> Role.Role_LG;
            case "두산 베어스" -> Role.Role_OB;
            case "키움 히어로즈" -> Role.Role_WO;
            case "한화 이글스" -> Role.Role_HH;
            case "SSG 랜더스" -> Role.Role_SK;
            case "NC 다이노스" -> Role.Role_NC;
            case "KT 위즈" -> Role.Role_KT;
            case "기아 타이거즈" -> Role.Role_HT;
            default -> Role.USER;
        };
        
        return role.getKey();
    }

    private String getRoleKeyByTeamId(String teamId) {
        if (teamId == null || teamId.trim().isEmpty()) {
            return Role.USER.getKey();
        }
        return "ROLE_" + teamId.toUpperCase();
    }

    private String getTeamIdByFavoriteTeamName(String teamName) {
        if (teamName == null || "없음".equals(teamName) || teamName.trim().isEmpty()) {
            return null;
        }

        return switch (teamName) {
            case "삼성 라이온즈" -> "SS";
            case "롯데 자이언츠" -> "LT";
            case "LG 트윈스" -> "LG";
            case "두산 베어스" -> "OB";
            case "키움 히어로즈" -> "WO";
            case "한화 이글스" -> "HH";
            case "SSG 랜더스" -> "SK";
            case "NC 다이노스" -> "NC";
            case "KT 위즈" -> "KT";
            case "기아 타이거즈" -> "HT";
            default -> null; 
        };
    }
}