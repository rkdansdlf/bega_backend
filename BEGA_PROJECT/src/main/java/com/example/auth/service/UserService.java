package com.example.auth.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.auth.dto.UserDto;
import com.example.auth.dto.SignupDto;
import com.example.mypage.dto.UserProfileDto;
import com.example.auth.entity.UserEntity;
import com.example.kbo.entity.TeamEntity;
import com.example.auth.entity.Role;
import com.example.auth.util.JWTUtil;
import com.example.auth.repository.UserRepository;
import com.example.auth.repository.UserBlockRepository;
import com.example.auth.repository.UserFollowRepository;
import com.example.kbo.repository.TeamRepository;
import com.example.kbo.util.TeamCodeNormalizer;
import com.example.auth.repository.RefreshRepository;

import com.example.common.exception.UserNotFoundException;
import com.example.common.exception.AuthenticationRequiredException;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.DuplicateHandleException;
import com.example.common.exception.TeamNotFoundException;
import com.example.common.exception.DuplicateEmailException;
import com.example.common.exception.DuplicateNameException;
import com.example.common.exception.InvalidAuthorException;
import com.example.common.exception.InvalidCredentialsException;
import com.example.common.exception.SocialLoginRequiredException;

import com.example.profile.storage.service.ProfileImageService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    static final ZoneId DAILY_BONUS_ZONE = ZoneId.of("Asia/Seoul");
    private static final int DAILY_LOGIN_BONUS_POINTS = 5;
    private static final Pattern HANDLE_PATTERN = Pattern.compile("^@[a-z0-9_]{1,14}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final RefreshRepository refreshRepository;
    private final UserFollowRepository userFollowRepository;
    private final UserBlockRepository userBlockRepository;
    private final com.example.auth.repository.UserProviderRepository userProviderRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final JWTUtil jwtUtil;
    private final ProfileImageService profileImageService;
    private final AccountDeletionService accountDeletionService;
    private final AccountSecurityService accountSecurityService;
    private final AuthSessionService authSessionService;

    public UserService(UserRepository userRepository,
            TeamRepository teamRepository,
            RefreshRepository refreshRepository,
            UserFollowRepository userFollowRepository,
            UserBlockRepository userBlockRepository,
            com.example.auth.repository.UserProviderRepository userProviderRepository,
            BCryptPasswordEncoder bCryptPasswordEncoder,
            JWTUtil jwtUtil,
            ProfileImageService profileImageService,
            AccountDeletionService accountDeletionService,
            AccountSecurityService accountSecurityService,
            AuthSessionService authSessionService) {
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.refreshRepository = refreshRepository;
        this.userFollowRepository = userFollowRepository;
        this.userBlockRepository = userBlockRepository;
        this.userProviderRepository = userProviderRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.jwtUtil = jwtUtil;
        this.profileImageService = profileImageService;
        this.accountDeletionService = accountDeletionService;
        this.accountSecurityService = accountSecurityService;
        this.authSessionService = authSessionService;
    }

    public JWTUtil getJWTUtil() {
        return jwtUtil;
    }

    /**
     * 회원가입
     */
    @Transactional
    public UserEntity saveUser(@NonNull SignupDto signupDto) {
        UserDto userDto = Objects.requireNonNull(signupDto.toUserDto());
        normalizeUserEmail(userDto);
        this.signUp(userDto);

        return findUserByEmailOrThrow(userDto.getEmail());
    }

    private void normalizeUserEmail(UserDto userDto) {
        if (userDto != null && userDto.getEmail() != null) {
            userDto.setEmail(normalizeEmail(userDto.getEmail()));
        }
    }

    /**
     * 회원가입 메인 로직
     */
    @Transactional
    public void signUp(@NonNull UserDto userDto) {
        normalizeUserEmail(userDto);
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
    private void createNewUser(@NonNull UserDto userDto) {
        String handle = validateAndNormalizeHandle(userDto.getHandle());
        if (userRepository.existsByHandle(handle)) {
            throw new DuplicateHandleException(handle);
        }

        String favoriteTeamName = userDto.getFavoriteTeam();
        String roleKey = getRoleKeyByFavoriteTeam(favoriteTeamName);
        String teamId = getTeamIdByFavoriteTeamName(favoriteTeamName);
        TeamEntity team = findTeamById(teamId);
        String encodedPassword = encodePasswordIfPresent(userDto.getPassword());

        UserEntity user = UserEntity.builder()
                .name(userDto.getName())
                .handle(handle)
                .uniqueId(java.util.UUID.randomUUID())
                .email(userDto.getEmail())
                .password(encodedPassword)
                .favoriteTeam(team)
                .role(roleKey)
                .provider(Optional.ofNullable(userDto.getProvider()).orElse("LOCAL"))
                .providerId(userDto.getProviderId())
                .build();

        try {
            userRepository.save(Objects.requireNonNull(user));
        } catch (DataIntegrityViolationException ex) {
            RuntimeException mapped = mapDuplicateUserConstraint(ex, user.getEmail(), handle);
            if (mapped != null) {
                throw mapped;
            }
            throw ex;
        }
    }

    /**
     * 핸들 유효성 검증 및 정규화
     */
    public String validateAndNormalizeHandle(String handle) {
        if (handle == null || handle.trim().isEmpty()) {
            throw new IllegalArgumentException("아이디(@handle)는 필수 입력 항목입니다.");
        }

        String trimmedHandle = handle.trim();
        if (!trimmedHandle.startsWith("@")) {
            trimmedHandle = "@" + trimmedHandle;
        }
        trimmedHandle = trimmedHandle.toLowerCase(Locale.ROOT);

        // 정책: 시작은 @, 최대 15자, 영문/숫자/_ 만 허용
        if (trimmedHandle.length() > 15) {
            throw new IllegalArgumentException("아이디(@handle)는 최대 15자까지 가능합니다.");
        }

        if (!HANDLE_PATTERN.matcher(trimmedHandle).matches()) {
            throw new IllegalArgumentException("아이디(@handle)는 영문, 숫자, 밑줄(_)만 포함할 수 있습니다.");
        }

        return trimmedHandle;
    }

    @Transactional(readOnly = true)
    public com.example.auth.dto.AvailabilityCheckResponseDto checkHandleAvailability(String handle) {
        String normalizedHandle = validateAndNormalizeHandle(handle);
        return new com.example.auth.dto.AvailabilityCheckResponseDto(
                !userRepository.existsByHandle(normalizedHandle),
                normalizedHandle);
    }

    @Transactional(readOnly = true)
    public com.example.auth.dto.AvailabilityCheckResponseDto checkEmailAvailability(String email) {
        String normalizedEmail = validateAndNormalizeEmail(email);
        return new com.example.auth.dto.AvailabilityCheckResponseDto(
                !userRepository.existsByEmail(normalizedEmail),
                normalizedEmail);
    }

    public String validateAndNormalizeEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isBlank()) {
            throw new BadRequestBusinessException("EMAIL_REQUIRED", "이메일을 입력해 주세요.");
        }
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new BadRequestBusinessException("EMAIL_INVALID", "유효하지 않은 이메일 형식입니다.");
        }
        return normalizedEmail;
    }

    /**
     * 로그인 인증 및 JWT 토큰 생성
     */
    public record LoginResult(
            String accessToken,
            String refreshToken,
            Map<String, Object> profileData) {
    }

    @Transactional
    public LoginResult authenticateAndGetToken(String email, String password) {
        return authenticateAndGetToken(email, password, null);
    }

    @Transactional
    public LoginResult authenticateAndGetToken(String email, String password, HttpServletRequest request) {
        String normalizedEmail = Optional.ofNullable(email).map(e -> e.trim().toLowerCase()).orElse(null);
        UserEntity user = findUserByEmailOrThrow(normalizedEmail);

        validateAuthorForLogin(user);
        validatePassword(user, password);

        // 일일 출석 보너스 체크
        int currentCheerPoints = checkAndApplyDailyLoginBonus(Objects.requireNonNull(user));

        int tokenVersion = Optional.ofNullable(user.getTokenVersion()).orElse(0);
        String accessToken = jwtUtil.createJwt(
                user.getEmail(),
                user.getRole(),
                user.getId(),
                jwtUtil.getAccessTokenExpirationTime(),
                tokenVersion);

        // Refresh Token 생성
        String refreshToken = authSessionService.issueRefreshToken(
                user.getEmail(),
                user.getRole(),
                user.getId(),
                tokenVersion,
                request);
        accountSecurityService.handleSuccessfulLogin(user, request);

        Map<String, Object> profileData = new HashMap<>();
        profileData.put("id", user.getId());
        profileData.put("name", user.getName());
        profileData.put("role", user.getRole());
        profileData.put("handle", user.getHandle());
        profileData.put("cheerPoints", currentCheerPoints);

        return new LoginResult(
                accessToken,
                refreshToken,
                profileData);
    }

    /**
     * 일일 출석 보너스 지급 (5포인트)
     * 마지막 로그인 날짜(lastLoginDate)를 확인하여 오늘 첫 로그인인 경우 지급
     */
    @Transactional
    public int checkAndApplyDailyLoginBonus(@NonNull UserEntity user) {
        LocalDate today = LocalDate.now(DAILY_BONUS_ZONE);

        boolean shouldAward = Optional.ofNullable(user.getLastBonusDate())
                .map(lastBonusDate -> lastBonusDate.isBefore(today))
                .orElse(true);

        int currentCheerPoints = Optional.ofNullable(user.getCheerPoints()).orElse(0);
        LocalDateTime now = LocalDateTime.now();
        if (shouldAward) {
            currentCheerPoints += DAILY_LOGIN_BONUS_POINTS;
            user.setCheerPoints(currentCheerPoints);
            user.setLastBonusDate(today);
            log.info("Daily Login Bonus (5 points) awarded to user: {}. Current Points: {}", user.getEmail(),
                    currentCheerPoints);
        } else if (user.getCheerPoints() == null) {
            user.setCheerPoints(currentCheerPoints);
        }

        user.setLastLoginDate(now);
        userRepository.save(user);

        return currentCheerPoints;
    }

    @Transactional
    public void deleteRefreshTokenByEmail(String email) {
        authSessionService.deleteRefreshTokenByEmail(email);
    }

    /**
     * 프로필 업데이트
     */
    @Transactional
    public UserEntity updateProfile(@NonNull Long id, @NonNull UserProfileDto updateDto) {
        UserEntity user = findUserById(id);

        updateUserName(user, updateDto.getName());
        updateProfileImage(user, updateDto.getProfileImageUrl());
        updateFavoriteTeam(user, updateDto.getFavoriteTeam());
        updateBio(user, updateDto.getBio());

        return userRepository.save(Objects.requireNonNull(user));
    }

    private void updateUserName(UserEntity user, String name) {
        user.setName(name);
    }

    private void updateProfileImage(UserEntity user, String imageUrl) {
        if (imageUrl != null && !imageUrl.isEmpty()) {
            user.setProfileImageUrl(imageUrl);
        }
    }

    private void updateBio(UserEntity user, String bio) {
        // null이거나 빈 문자열이면 업데이트 (삭제 가능)
        if (bio != null) {
            String trimmedBio = bio.trim();
            if (trimmedBio.length() > 500) {
                trimmedBio = trimmedBio.substring(0, 500);
            }
            user.setBio(trimmedBio);
        }
    }

    /**
     * 응원팀 업데이트 (관리자가 아닌 경우에만 팀 확인)
     */
    private void updateFavoriteTeam(UserEntity user, String teamId) {
        if (teamId != null && !teamId.trim().isEmpty()) {
            String normalizedTeamId = TeamCodeNormalizer.normalize(teamId);
            TeamEntity team = teamRepository.findByTeamIdAndIsActive(normalizedTeamId, true)
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
    /**
     * 비밀번호 변경 (소셜 로그인 사용자도 비밀번호 설정 가능)
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        UserEntity user = findUserById(Objects.requireNonNull(userId));

        // 현재 비밀번호가 설정되어 있는 경우에만 검증
        if (user.getPassword() != null) {
            if (currentPassword == null || currentPassword.isEmpty()) {
                throw new BadRequestBusinessException("CURRENT_PASSWORD_REQUIRED", "현재 비밀번호를 입력해주세요.");
            }
            if (!bCryptPasswordEncoder.matches(currentPassword, user.getPassword())) {
                throw new InvalidCredentialsException("현재 비밀번호가 일치하지 않습니다.");
            }
        }

        // 새 비밀번호 암호화 및 저장
        user.setPassword(bCryptPasswordEncoder.encode(newPassword));
        user.setTokenVersion(Optional.ofNullable(user.getTokenVersion()).orElse(0) + 1);
        userRepository.save(user);
        refreshRepository.deleteByEmail(user.getEmail());
        accountSecurityService.recordPasswordChanged(userId);

        log.info("Password changed/set for user ID: {}", userId);
    }

    /**
     * 계정 삭제 (회원탈퇴)
     */
    @Transactional
    public LocalDateTime deleteAccount(Long userId, String password) {
        return accountDeletionService.scheduleAccountDeletion(userId, password);
    }

    /**
     * 연동된 소셜 계정 목록 조회
     */
    @Transactional(readOnly = true)
    public java.util.List<com.example.mypage.dto.UserProviderDto> getConnectedProviders(Long userId) {
        return userProviderRepository.findByUserId(userId).stream()
                .map(provider -> com.example.mypage.dto.UserProviderDto.builder()
                        .provider(provider.getProvider())
                        .providerId(provider.getProviderId())
                        .email(provider.getEmail())
                        .connectedAt(provider.getConnectedAt() != null
                                ? java.time.format.DateTimeFormatter.ISO_INSTANT.format(provider.getConnectedAt())
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
        UserEntity user = findUserById(Objects.requireNonNull(userId));
        boolean hasPassword = user.getPassword() != null && !user.getPassword().startsWith("oauth2_user"); // "oauth2_user"
                                                                                                           // is
                                                                                                           // deprecated
                                                                                                           // but
                                                                                                           // checking
                                                                                                           // just in
                                                                                                           // case

        long linkedCount = userProviderRepository.findByUserId(userId).size();

        if (!hasPassword && linkedCount <= 1) {
            throw new BadRequestBusinessException(
                    "LOGIN_METHOD_REQUIRED",
                    "최소 하나의 로그인 방식(비밀번호 또는 소셜 연동)이 존재해야 합니다.");
        }

        userProviderRepository.deleteByUserIdAndProvider(userId, provider);
        accountSecurityService.recordProviderUnlinked(userId, provider);
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
    public UserEntity findUserById(@NonNull Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    /**
     * 이메일 중복 체크
     */
    @Transactional(readOnly = true)
    public boolean isEmailExists(String email) {
        return userRepository.existsByEmail(normalizeEmail(email));
    }

    /**
     * 이메일로 사용자 ID 조회
     */
    @Transactional(readOnly = true)
    public Long getUserIdByEmail(String email) {
        String input = Optional.ofNullable(email).map(String::trim).orElse("");
        if (input.isBlank()) {
            throw new UserNotFoundException("email", email);
        }

        // 1. Try finding by email
        Optional<UserEntity> userByEmail = userRepository.findByEmail(normalizeEmail(input));
        if (userByEmail.isPresent()) {
            return userByEmail.get().getId();
        }

        // 2. Try parsing as Long (userId) for migration compatibility
        return tryParseLong(input)
                .flatMap(userRepository::findById)
                .map(UserEntity::getId)
                .orElseThrow(() -> new UserNotFoundException("email/id", email));
    }

    private Optional<Long> tryParseLong(String value) {
        try {
            return Optional.of(Long.valueOf(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * 이메일로 사용자 DTO 조회
     */
    @Transactional(readOnly = true)
    public UserDto findUserByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        return userRepository.findByEmail(normalizedEmail)
                .map(this::convertToUserDto)
                .orElseThrow(() -> new UserNotFoundException("email", email));
    }

    /**
     * 공개 프로필 조회 (핸들 기준)
     */
    @Transactional(readOnly = true)
    public com.example.auth.dto.PublicUserProfileDto getPublicUserProfileByHandle(String handle, Long currentUserId) {
        UserEntity user = findUserByHandleOrThrow(handle);
        validatePublicProfileAccess(user, currentUserId);
        return toPublicUserProfile(user);
    }

    private com.example.auth.dto.PublicUserProfileDto toPublicUserProfile(UserEntity user) {
        return com.example.auth.dto.PublicUserProfileDto.builder()
                .name(user.getName())
                .handle(user.getHandle())
                .favoriteTeam(user.getFavoriteTeamId())
                .profileImageUrl(resolvePublicProfileImageUrl(user.getProfileImageUrl()))
                .bio(user.getBio())
                .cheerPoints(user.getCheerPoints())
                .build();
    }

    private UserEntity findUserByHandleOrThrow(String handle) {
        String normalizedHandle = normalizeHandle(handle);
        return userRepository.findByHandle(normalizedHandle)
                .orElseThrow(() -> new UserNotFoundException("handle", handle));
    }

    @Transactional(readOnly = true)
    public Long getUserIdByHandle(String handle) {
        return findUserByHandleOrThrow(handle).getId();
    }

    private String normalizeHandle(String handle) {
        if (handle == null || handle.isBlank()) {
            throw new UserNotFoundException("handle", String.valueOf(handle));
        }
        String normalized = handle.trim();
        if (!normalized.startsWith("@")) {
            normalized = "@" + normalized;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private void validatePublicProfileAccess(UserEntity target, Long currentUserId) {
        Long targetUserId = target.getId();
        if (targetUserId == null) {
            throw new UserNotFoundException("userId", "null");
        }

        if (currentUserId != null && currentUserId.equals(targetUserId)) {
            return;
        }

        if (currentUserId != null && userBlockRepository.existsBidirectionalBlock(currentUserId, targetUserId)) {
            throw new AccessDeniedException("차단 관계인 사용자의 프로필은 조회할 수 없습니다.");
        }

        if (!target.isPrivateAccount()) {
            return;
        }

        if (currentUserId != null
                && userFollowRepository.existsById(new com.example.auth.entity.UserFollow.Id(currentUserId, targetUserId))) {
            return;
        }

        throw new AccessDeniedException("비공개 계정의 프로필은 팔로워만 조회할 수 있습니다.");
    }

    private String resolvePublicProfileImageUrl(String profileImageUrl) {
        if (profileImageUrl == null || profileImageUrl.isBlank()) {
            return null;
        }
        try {
            return profileImageService.getProfileImageUrl(profileImageUrl);
        } catch (Exception e) {
            log.warn("Failed to resolve public profile image URL: {}", e.getMessage());
            return null;
        }
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
     * 로그인 전 계정 상태 검증
     * - 비활성 계정(enabled=false) 차단
     * - 잠금 계정(locked=true) 차단, 단 잠금 만료 시 허용
     */
    private void validateAuthorForLogin(UserEntity user) {
        if (user.isPendingDeletion()) {
            throw new InvalidAuthorException("계정 삭제 예약 상태입니다. 이메일의 복구 링크를 확인해주세요.");
        }

        if (!user.isEnabled()) {
            throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
        }

        if (!isAccountUsable(user)) {
            throw new InvalidAuthorException("계정이 잠겨 있습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private boolean isAccountUsable(UserEntity user) {
        if (!user.isLocked()) {
            return true;
        }

        if (user.getLockExpiresAt() == null) {
            return false;
        }

        return user.getLockExpiresAt().isBefore(LocalDateTime.now());
    }

    /**
     * 이메일로 사용자 조회 (로그인용)
     */
    private UserEntity findUserByEmailOrThrow(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new InvalidCredentialsException());
    }

    private String normalizeEmail(String email) {
        return Optional.ofNullable(email)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .orElse("");
    }

    private RuntimeException mapDuplicateUserConstraint(
            DataIntegrityViolationException exception,
            String email,
            String handle) {
        String message = exception.getMostSpecificCause() != null
                ? exception.getMostSpecificCause().getMessage()
                : exception.getMessage();
        if (message == null) {
            return null;
        }

        String loweredMessage = message.toLowerCase(Locale.ROOT);
        if (loweredMessage.contains("uq_users_handle")
                || loweredMessage.contains(" users(handle)")
                || loweredMessage.contains(" users (handle)")
                || loweredMessage.contains(".handle")) {
            return new DuplicateHandleException(handle);
        }

        if (loweredMessage.contains("uq_users_email")
                || loweredMessage.contains(" users(email)")
                || loweredMessage.contains(" users (email)")
                || loweredMessage.contains(".email")) {
            return new DuplicateEmailException(email);
        }

        return null;
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
                .handle(userEntity.getHandle())
                .name(userEntity.getName())
                .email(userEntity.getEmail())
                .favoriteTeam(userEntity.getFavoriteTeamId())
                .role(userEntity.getRole())
                .provider(userEntity.getProvider())
                .providerId(userEntity.getProviderId())
                .cheerPoints(userEntity.getCheerPoints())
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
            case "두산 베어스" -> "DB";
            case "키움 히어로즈" -> "KH";
            case "한화 이글스" -> "HH";
            case "SSG 랜더스" -> "SSG";
            case "NC 다이노스" -> "NC";
            case "KT 위즈" -> "KT";
            case "기아 타이거즈", "KIA 타이거즈" -> "KIA";
            default -> null;
        };
    }

    @Transactional(readOnly = true)
    public String ensureNameAvailable(Long userId, String name) {
        if (userId == null) {
            throw new AuthenticationRequiredException("인증이 필요합니다.");
        }

        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isBlank()) {
            throw new BadRequestBusinessException("NAME_REQUIRED", "닉네임을 입력해 주세요.");
        }
        if (normalizedName.length() < 2) {
            throw new BadRequestBusinessException("NAME_TOO_SHORT", "닉네임은 최소 2자 이상이어야 합니다.");
        }
        if (normalizedName.length() > 20) {
            throw new BadRequestBusinessException("NAME_TOO_LONG", "닉네임은 20자 이하여야 합니다.");
        }

        UserEntity target = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        Optional<UserEntity> existing = userRepository.findByNameIgnoreCase(normalizedName);
        if (existing.isPresent() && !existing.get().getId().equals(target.getId())) {
            throw new DuplicateNameException(normalizedName);
        }

        return normalizedName;
    }
}
