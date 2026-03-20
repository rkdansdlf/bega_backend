package com.example.mypage.controller;

import com.example.common.dto.ApiResponse;
import com.example.common.exception.AuthenticationRequiredException;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.ConflictBusinessException;
import com.example.common.exception.NotFoundBusinessException;
import com.example.common.exception.UnauthorizedBusinessException;
import com.example.common.exception.UserNotFoundException;
import com.example.auth.entity.UserEntity;
import com.example.mypage.dto.UserProfileDto;
import com.example.mypage.dto.DeviceSessionDto;
import com.example.auth.entity.RefreshToken;
import com.example.auth.service.AccountSecurityService;
import com.example.auth.service.AuthSessionService;
import com.example.auth.service.AuthSessionMetadataResolver;
import com.example.auth.service.PolicyConsentService;
import com.example.auth.util.AuthCookieUtil;

import com.example.auth.service.UserService;
import com.example.auth.util.JWTUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.example.profile.storage.service.ProfileImageService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

//마이페이지 기능을 위한 컨트롤러입니다.
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class MypageController {
        private final UserService userService;
        private final JWTUtil jwtUtil;
        private final ProfileImageService profileImageService;
        private final AuthCookieUtil authCookieUtil;
        private final PolicyConsentService policyConsentService;
        private final AccountSecurityService accountSecurityService;
        private final AuthSessionService authSessionService;

        // 프로필 정보 조회 (GET /mypage) - 수정 없음
        @GetMapping("/mypage")
        public ResponseEntity<ApiResponse> getMyProfile(
                        @AuthenticationPrincipal Long userId) {
                UserEntity userEntity = requireAuthenticatedUser(userId, "요청한 사용자의 프로필 정보를 찾을 수 없습니다. (재로그인 필요)");
                PolicyConsentService.PolicyConsentStatus policyConsentStatus = policyConsentService
                                .evaluatePolicyConsentStatus(userEntity.getId());

                UserProfileDto profileDto = UserProfileDto.builder()
                                .id(userEntity.getId())
                                .name(userEntity.getName())
                                .handle(userEntity.getHandle())
                                .email(userEntity.getEmail())
                                .favoriteTeam(userEntity.getFavoriteTeamId() != null
                                                ? userEntity.getFavoriteTeamId()
                                                : "없음")
                                .profileImageUrl(profileImageService
                                                .getProfileImageUrl(userEntity.getProfileImageUrl()))
                                .createdAt(userEntity.getCreatedAt() != null
                                                ? userEntity.getCreatedAt()
                                                                .atZone(java.time.ZoneId.of("Asia/Seoul"))
                                                                .format(DateTimeFormatter.ISO_DATE_TIME)
                                                : null)
                                .role(userEntity.getRole())
                                .bio(userEntity.getBio())
                                .cheerPoints(userEntity.getCheerPoints())
                                .provider(userEntity.getProvider())
                                .providerId(userEntity.getProviderId())
                                .hasPassword(userEntity.getPassword() != null)
                                .policyConsentRequired(policyConsentStatus.policyConsentRequired())
                                .policyConsentNoticeRequired(policyConsentStatus.policyConsentNoticeRequired())
                                .missingPolicyTypes(policyConsentStatus.missingPolicyTypes())
                                .policyConsentEffectiveDate(policyConsentStatus.effectiveDate())
                                .policyConsentHardGateDate(policyConsentStatus.hardGateDate())
                                .build();

                log.info("getMyProfile - userId: {}, email: {}, points: {}", userId, userEntity.getEmail(),
                                userEntity.getCheerPoints());
                return ResponseEntity.ok(ApiResponse.success("프로필 조회 성공", profileDto));
        }

        // 프로필 정보 수정 (PUT /mypage)
        @PutMapping("/mypage")
        public ResponseEntity<ApiResponse> updateMyProfile(
                        @AuthenticationPrincipal Long userId,
                        @Valid @RequestBody UserProfileDto updateDto) {
                Long authenticatedUserId = requireAuthenticatedUserId(userId);
                if (updateDto.getName() == null || updateDto.getName().trim().isEmpty()) {
                        throw new BadRequestBusinessException("PROFILE_NAME_REQUIRED", "이름/닉네임은 필수 입력 항목입니다.");
                }

                requireAuthenticatedUser(authenticatedUserId, "요청한 사용자의 프로필 정보를 찾을 수 없습니다. (재로그인 필요)");
                UserEntity updatedEntity = userService.updateProfile(authenticatedUserId, updateDto);

                String newRoleKey = updatedEntity.getRole();
                String userEmail = updatedEntity.getEmail();
                int tokenVersion = updatedEntity.getTokenVersion() == null ? 0 : updatedEntity.getTokenVersion();
                long accessTokenExpiredMs = jwtUtil.getAccessTokenExpirationTime();

                String newJwtToken = jwtUtil.createJwt(userEmail, newRoleKey, authenticatedUserId,
                                accessTokenExpiredMs, tokenVersion);

                ResponseCookie cookie = authCookieUtil.buildAuthCookie(newJwtToken, accessTokenExpiredMs / 1000);

                Map<String, Object> responseMap = new HashMap<>();
                responseMap.put("profileImageUrl",
                                profileImageService.getProfileImageUrl(updatedEntity.getProfileImageUrl()));
                responseMap.put("name", updatedEntity.getName());
                responseMap.put("handle", updatedEntity.getHandle());
                responseMap.put("email", updatedEntity.getEmail());
                responseMap.put("favoriteTeam",
                                updatedEntity.getFavoriteTeamId() != null ? updatedEntity.getFavoriteTeamId() : "없음");
                responseMap.put("bio", updatedEntity.getBio());

                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                                .body(ApiResponse.success("프로필 수정 성공 및 JWT 쿠키 재설정 완료", responseMap));
        }

        /**
         * 비밀번호 변경 (일반 로그인 사용자만)
         */
        @PutMapping("/password")
        public ResponseEntity<ApiResponse> changePassword(
                        @AuthenticationPrincipal Long userId,
                        @Valid @RequestBody com.example.mypage.dto.ChangePasswordRequest request) {
                Long authenticatedUserId = requireAuthenticatedUserId(userId);
                requireAuthenticatedUser(authenticatedUserId, "요청한 사용자의 프로필 정보를 찾을 수 없습니다. (재로그인 필요)");
                userService.changePassword(authenticatedUserId, request.getCurrentPassword(), request.getNewPassword());

                ResponseCookie expireAuthCookie = authCookieUtil.buildExpiredAuthCookie();
                ResponseCookie expireRefreshCookie = authCookieUtil.buildExpiredRefreshCookie();

                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, expireAuthCookie.toString())
                                .header(HttpHeaders.SET_COOKIE, expireRefreshCookie.toString())
                                .body(ApiResponse.success("비밀번호가 성공적으로 변경되었습니다."));
        }

        /**
         * 계정 삭제 (회원탈퇴)
         */
        @DeleteMapping("/account")
        public ResponseEntity<ApiResponse> deleteAccount(
                        @AuthenticationPrincipal Long userId,
                        @RequestBody(required = false) com.example.mypage.dto.DeleteAccountRequest request) {
                Long authenticatedUserId = requireAuthenticatedUserId(userId);
                requireAuthenticatedUser(authenticatedUserId, "요청한 사용자의 프로필 정보를 찾을 수 없습니다. (재로그인 필요)");

                String password = request != null ? request.getPassword() : null;
                LocalDateTime scheduledFor = userService.deleteAccount(authenticatedUserId, password);

                ResponseCookie authCookie = authCookieUtil.buildExpiredAuthCookie();
                ResponseCookie refreshCookie = authCookieUtil.buildExpiredRefreshCookie();

                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, authCookie.toString())
                                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                                .body(ApiResponse.success(
                                                "계정 삭제가 예약되었습니다.",
                                                Map.of("scheduledFor", scheduledFor == null ? null : scheduledFor.toString())));
        }

        /**
         * 연동된 계정 목록 조회
         */
        @GetMapping("/providers")
        public ResponseEntity<ApiResponse> getConnectedProviders(@AuthenticationPrincipal Long userId) {
                Long authenticatedUserId = requireAuthenticatedUserId(userId);
                requireAuthenticatedUser(authenticatedUserId, "요청한 사용자의 프로필 정보를 찾을 수 없습니다. (재로그인 필요)");
                java.util.List<com.example.mypage.dto.UserProviderDto> providers = userService
                                .getConnectedProviders(authenticatedUserId);
                return ResponseEntity.ok(ApiResponse.success("연동된 계정 목록 조회 성공", providers));
        }

        /**
         * 계정 연동 해제
         */
        @DeleteMapping("/providers/{provider}")
        public ResponseEntity<ApiResponse> unlinkProvider(
                        @AuthenticationPrincipal Long userId,
                        @PathVariable String provider) {
                Long authenticatedUserId = requireAuthenticatedUserId(userId);
                requireAuthenticatedUser(authenticatedUserId, "요청한 사용자의 프로필 정보를 찾을 수 없습니다. (재로그인 필요)");
                userService.unlinkProvider(authenticatedUserId, provider);
                return ResponseEntity.ok(ApiResponse.success("계정 연동이 해제되었습니다."));
        }

        /**
         * 로그인 기기 목록 조회
         */
        @GetMapping("/sessions")
        public ResponseEntity<ApiResponse> getSessions(
                        @AuthenticationPrincipal Long userId,
                        HttpServletRequest request) {
                UserEntity user = requireAuthenticatedUser(userId, "요청한 사용자의 프로필 정보를 찾을 수 없습니다.");
                List<RefreshToken> refreshTokens = authSessionService.findRefreshTokensByEmail(user.getEmail());
                RefreshToken currentSessionToken = authSessionService.resolveCurrentSessionToken(refreshTokens, request);
                String currentSessionId = authSessionService.resolveSessionIdentifier(currentSessionToken);
                AuthSessionMetadataResolver.SessionMetadata requestMetadata = authSessionService.resolveRequestMetadata(request);

                List<DeviceSessionDto> sessions = refreshTokens.stream()
                                .map(token -> buildDeviceSessionDto(token, currentSessionId, requestMetadata))
                                .sorted((left, right) -> {
                                        if (left.isCurrent() != right.isCurrent()) {
                                                return left.isCurrent() ? -1 : 1;
                                        }

                                        long leftActiveAt = parseSessionTime(left.getLastActiveAt());
                                        long rightActiveAt = parseSessionTime(right.getLastActiveAt());
                                        return Long.compare(rightActiveAt, leftActiveAt);
                                })
                                .toList();

                return ResponseEntity.ok(ApiResponse.success("기기 목록 조회 성공", sessions));
        }

        /**
         * 특정 기기 세션 종료
         */
        @DeleteMapping("/sessions/{sessionId}")
        public ResponseEntity<ApiResponse> deleteSession(
                        @AuthenticationPrincipal Long userId,
                        @PathVariable("sessionId") String sessionId,
                        HttpServletRequest request) {
                UserEntity user = requireAuthenticatedUser(userId, "요청한 사용자의 프로필 정보를 찾을 수 없습니다.");

                List<RefreshToken> refreshTokens = authSessionService.findRefreshTokensByEmail(user.getEmail());
                RefreshToken currentSessionToken = authSessionService.resolveCurrentSessionToken(refreshTokens, request);
                String currentSessionId = authSessionService.resolveSessionIdentifier(currentSessionToken);
                RefreshToken targetToken = refreshTokens.stream()
                                .filter(item -> sessionId.equals(authSessionService.resolveSessionIdentifier(item)))
                                .findFirst()
                                .orElse(null);

                if (targetToken == null) {
                        throw new NotFoundBusinessException("SESSION_NOT_FOUND", "종료할 세션 정보를 찾을 수 없습니다.");
                }

                if (Objects.equals(currentSessionId, sessionId)
                                && authSessionService.extractCookieValue(request, "Refresh") != null) {
                        throw new BadRequestBusinessException(
                                        "CURRENT_SESSION_REVOKE_NOT_ALLOWED",
                                        "현재 사용 중인 세션은 직접 종료할 수 없습니다.");
                }

                authSessionService.deleteRefreshToken(targetToken);
                accountSecurityService.recordSessionRevoked(user.getId(), targetToken);
                return ResponseEntity.ok(ApiResponse.success("선택한 세션을 종료했습니다."));
        }

        /**
         * 현재 세션 제외 세션 정리
         */
        @DeleteMapping("/sessions")
        public ResponseEntity<ApiResponse> deleteSessions(
                        @AuthenticationPrincipal Long userId,
                        @RequestParam(name = "allExceptCurrent", defaultValue = "false") boolean allExceptCurrent,
                        HttpServletRequest request) {
                UserEntity user = requireAuthenticatedUser(userId, "요청한 사용자의 프로필 정보를 찾을 수 없습니다.");
                if (!allExceptCurrent) {
                        throw new BadRequestBusinessException("UNSUPPORTED_SESSION_CLEANUP_REQUEST", "지원되지 않는 요청입니다.");
                }

                List<RefreshToken> refreshTokens = authSessionService.findRefreshTokensByEmail(user.getEmail());
                RefreshToken currentSessionToken = authSessionService.resolveCurrentSessionToken(refreshTokens, request);

                if (currentSessionToken == null) {
                        throw new ConflictBusinessException(
                                        "CURRENT_SESSION_NOT_RESOLVED",
                                        "현재 세션을 확인하지 못해 다른 기기 로그아웃을 중단했습니다. 다시 로그인 후 시도해주세요.");
                }

                List<RefreshToken> targets = refreshTokens.stream()
                                .filter(token -> !Objects.equals(
                                                authSessionService.resolveSessionIdentifier(token),
                                                authSessionService.resolveSessionIdentifier(currentSessionToken)))
                                .toList();

                if (targets.isEmpty()) {
                        return ResponseEntity.ok(ApiResponse.success("종료할 다른 세션이 없습니다."));
                }

                int revokedCount = targets.size();
                authSessionService.deleteRefreshTokens(targets);
                accountSecurityService.recordOtherSessionsRevoked(userId, revokedCount);
                return ResponseEntity.ok(ApiResponse.success("현재 기기 제외 다른 기기 로그아웃이 완료되었습니다."));
        }

        private Long requireAuthenticatedUserId(Long userId) {
                if (userId == null) {
                        throw new AuthenticationRequiredException("인증이 필요합니다.");
                }
                return userId;
        }

        private UserEntity requireAuthenticatedUser(Long userId, String notFoundMessage) {
                Long authenticatedUserId = requireAuthenticatedUserId(userId);
                try {
                        return userService.findUserById(authenticatedUserId);
                } catch (UserNotFoundException ex) {
                        throw new UnauthorizedBusinessException("AUTHENTICATED_USER_NOT_FOUND", notFoundMessage);
                }
        }

        private DeviceSessionDto buildDeviceSessionDto(RefreshToken refreshToken, String currentSessionId,
                        AuthSessionMetadataResolver.SessionMetadata requestMetadata) {
                boolean isExpired = authSessionService.isRefreshTokenExpired(refreshToken);
                String sessionId = authSessionService.resolveSessionIdentifier(refreshToken);
                boolean isCurrentSession = sessionId != null && currentSessionId != null
                                ? sessionId.equals(currentSessionId) && !isExpired
                                : false;

                String deviceType = authSessionService.normalizeText(refreshToken.getDeviceType(), "desktop");
                String deviceLabel = authSessionService.normalizeText(
                                refreshToken.getDeviceLabel(),
                                isCurrentSession ? requestMetadata.deviceLabel() : null);
                String browser = authSessionService.normalizeText(
                                refreshToken.getBrowser(),
                                isCurrentSession ? requestMetadata.browser() : "Unknown");
                String os = authSessionService.normalizeText(
                                refreshToken.getOs(),
                                isCurrentSession ? requestMetadata.os() : "Unknown");
                String ipAddress = authSessionService.normalizeText(
                                refreshToken.getIp(),
                                isCurrentSession ? requestMetadata.ip() : null);

                if (deviceLabel == null) {
                        deviceLabel = isCurrentSession ? requestMetadata.deviceLabel() : "알 수 없는 기기";
                }

                String lastSeenAt = formatSessionTime(refreshToken.getLastSeenAt());
                String lastActiveAt = lastSeenAt;
                if (lastActiveAt == null && isCurrentSession) {
                        lastActiveAt = formatSessionTime(LocalDateTime.now());
                }

                return DeviceSessionDto.builder()
                                .id(sessionId)
                                .sessionName(deviceLabel)
                                .deviceLabel(deviceLabel)
                                .deviceType(deviceType)
                                .browser(browser)
                                .os(os)
                                .lastActiveAt(lastActiveAt)
                                .lastSeenAt(lastSeenAt)
                                .isCurrent(isCurrentSession)
                                .isRevoked(isExpired)
                                .ip(ipAddress)
                                .build();
        }

        private String formatSessionTime(LocalDateTime value) {
                if (value == null) {
                        return null;
                }

                return value.atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }

        private long parseSessionTime(String value) {
                if (value == null || value.isBlank()) {
                        return 0L;
                }

                try {
                        return Instant.parse(value).toEpochMilli();
                } catch (Exception e) {
                        try {
                                return ZonedDateTime.parse(value).toInstant().toEpochMilli();
                        } catch (Exception parseException) {
                                try {
                                        LocalDateTime localDateTime = LocalDateTime.parse(value);
                                        return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                                } catch (Exception ignored) {
                                        return 0L;
                                }
                        }
                }
        }

}
