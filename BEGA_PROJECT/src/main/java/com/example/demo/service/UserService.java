package com.example.demo.service;

import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;

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
import com.example.demo.repo.RefreshRepository;
import com.example.demo.entity.RefreshToken;

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
    private final RefreshRepository refreshRepository;
    private final com.example.demo.repo.UserProviderRepository userProviderRepository; // Inject repository
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final JWTUtil jwtUtil;

    public JWTUtil getJWTUtil() {
        return jwtUtil;
    }

    /**
     * 회원가입
     */
    @Transactional
    public UserEntity saveUser(SignupDto signupDto) {
        UserDto userDto = signupDto.toUserDto();
        if (userDto.getEmail() != null) {
            userDto.setEmail(userDto.getEmail().trim().toLowerCase());
        }
        this.signUp(userDto);

        return findUserByEmailOrThrow(userDto.getEmail());
    }

    /**
     * 회원가입 메인 로직
     */
    @Transactional
    public void signUp(UserDto userDto) {
        if (userDto.getEmail() != null) {
            userDto.setEmail(userDto.getEmail().trim().toLowerCase());
        }
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
    @SuppressWarnings("null")
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
    @Transactional
    public Map<String, Object> authenticateAndGetToken(String email, String password) {
        String normalizedEmail = (email != null) ? email.trim().toLowerCase() : null;
        UserEntity user = findUserByEmailOrThrow(normalizedEmail);

        validatePassword(user, password);

        String accessToken = jwtUtil.createJwt(
                user.getEmail(),
                user.getRole(),
                user.getId(),
                ACCESS_EXPIRATION_TIME);

        // Refresh Token 생성
        String refreshToken = jwtUtil.createRefreshToken(user.getEmail(), user.getRole(), user.getId());

        // Refresh Token DB 저장
        saveOrUpdateRefreshToken(user.getEmail(), refreshToken);

        return Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "id", user.getId(),
                "name", user.getName(),
                "role", user.getRole());
    }

    /**
     * 리프레시 토큰 저장 또는 업데이트
     */
    @Transactional
    public void deleteRefreshTokenByEmail(String email) {
        RefreshToken token = refreshRepository.findByEmail(email);
        if (token != null) {
            refreshRepository.delete(token);
        }
    }

    /**
     * 리프레시 토큰 저장 또는 업데이트
     */
    @Transactional
    private void saveOrUpdateRefreshToken(String email, String token) {
        RefreshToken existingToken = refreshRepository.findByEmail(email);

        if (existingToken != null) {
            existingToken.setToken(token);
            existingToken.setExpiryDate(LocalDateTime.now().plusWeeks(1)); // 1주
            refreshRepository.save(existingToken);
        } else {
            RefreshToken newToken = new RefreshToken();
            newToken.setEmail(email);
            newToken.setToken(token);
            newToken.setExpiryDate(LocalDateTime.now().plusWeeks(1));
            refreshRepository.save(newToken);
        }
    }

    /**
     * 프로필 업데이트
     */
    @Transactional
    @SuppressWarnings("null")
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
     * 응원팀 업데이트 (관리자가 아닌 경우에만 팀 확인)
     */
    private void updateFavoriteTeam(UserEntity user, String teamId) {
        if (teamId != null && !teamId.trim().isEmpty()) {
            TeamEntity team = teamRepository.findById(teamId)
                    .orElseThrow(() -> new TeamNotFoundException(teamId));
            user.setFavoriteTeam(team);
        } else {
            user.setFavoriteTeam(null);
        }
        // 팀 변경 시 role은 변경하지 않음 (ADMIN/USER 유지)
    }

    /**
     * 비밀번호 변경 (일반 로그인 사용자만)
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        UserEntity user = findUserById(userId);

        // OAuth2 사용자 체크
        if (user.isOAuth2User()) {
            throw new IllegalStateException("소셜 로그인 사용자는 비밀번호를 변경할 수 없습니다.");
        }

        // 현재 비밀번호가 없는 경우 (소셜 로그인 후 로컬 연동 등)
        if (user.getPassword() == null) {
            throw new IllegalStateException("비밀번호가 설정되어 있지 않습니다.");
        }

        // 현재 비밀번호 검증
        if (!bCryptPasswordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidCredentialsException("현재 비밀번호가 일치하지 않습니다.");
        }

        // 새 비밀번호 암호화 및 저장
        user.setPassword(bCryptPasswordEncoder.encode(newPassword));
        userRepository.save(user);

        log.info("Password changed for user ID: {}", userId);
    }

    /**
     * 계정 삭제 (회원탈퇴)
     */
    @Transactional
    public void deleteAccount(Long userId, String password) {
        UserEntity user = findUserById(userId);

        // LOCAL 사용자는 비밀번호 확인 필요
        if (!user.isOAuth2User()) {
            if (password == null || password.isEmpty()) {
                throw new IllegalArgumentException("비밀번호를 입력해주세요.");
            }
            if (user.getPassword() == null) {
                throw new IllegalStateException("비밀번호가 설정되어 있지 않습니다.");
            }
            if (!bCryptPasswordEncoder.matches(password, user.getPassword())) {
                throw new InvalidCredentialsException("비밀번호가 일치하지 않습니다.");
            }
        }

        String userEmail = user.getEmail();

        // Refresh Token 삭제
        RefreshToken refreshToken = refreshRepository.findByEmail(userEmail);
        if (refreshToken != null) {
            refreshRepository.delete(refreshToken);
        }

        // 사용자 삭제 (관련 데이터는 DB의 CASCADE 설정에 따라 처리됨)
        userRepository.delete(user);

        log.info("Account deleted for user ID: {}, email: {}", userId, userEmail);
    }

    /**
     * 연동된 소셜 계정 목록 조회
     */
    @Transactional(readOnly = true)
    public java.util.List<com.example.demo.mypage.dto.UserProviderDto> getConnectedProviders(Long userId) {
        return userProviderRepository.findByUserId(userId).stream()
                .map(provider -> com.example.demo.mypage.dto.UserProviderDto.builder()
                        .provider(provider.getProvider())
                        .connectedAt(provider.getConnectedAt() != null
                                ? provider.getConnectedAt().format(java.time.format.DateTimeFormatter.ISO_DATE_TIME)
                                : null)
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 소셜 계정 연동 해제
     */
    @Transactional
    public void unlinkProvider(Long userId, String provider) {
        // 최소 1개의 로그인 방식은 남겨둬야 함 (비밀번호가 있거나, 다른 연동 계정이 있어야 함)
        UserEntity user = findUserById(userId);
        boolean hasPassword = user.getPassword() != null && !user.getPassword().startsWith("oauth2_user"); // "oauth2_user"
                                                                                                           // is
                                                                                                           // deprecated
                                                                                                           // but
                                                                                                           // checking
                                                                                                           // just in
                                                                                                           // case

        long linkedCount = userProviderRepository.findByUserId(userId).size();

        if (!hasPassword && linkedCount <= 1) {
            throw new IllegalStateException("최소 하나의 로그인 방식(비밀번호 또는 소셜 연동)이 존재해야 합니다.");
        }

        userProviderRepository.deleteByUserIdAndProvider(userId, provider);
        log.info("Unlinked info for user ID: {}, provider: {}", userId, provider);
    }

    /**
     * 사용자가 카카오 또는 네이버로 연동되어 있는지 확인 (메이트 인증용)
     */
    @Transactional(readOnly = true)
    public boolean isSocialVerified(Long userId) {
        return userProviderRepository.findByUserId(userId).stream()
                .anyMatch(p -> "kakao".equalsIgnoreCase(p.getProvider()) || "naver".equalsIgnoreCase(p.getProvider()));
    }

    /**
     * ID로 사용자 조회
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("null")
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
        String normalizedEmail = email.trim().toLowerCase();
        return userRepository.findByEmail(normalizedEmail)
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
     * 일반 회원가입 시 항상 USER 역할 부여 (팀과 무관)
     */
    private String getRoleKeyByFavoriteTeam(String teamName) {
        // 모든 일반 가입자는 ROLE_USER
        return Role.USER.getKey();
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