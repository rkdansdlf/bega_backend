package com.example.mypage.controller;

import com.example.common.dto.ApiResponse;
import com.example.auth.entity.UserEntity;
import com.example.mypage.dto.UserProfileDto;
import com.example.mypage.dto.DeviceSessionDto;
import com.example.auth.entity.RefreshToken;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.service.PolicyConsentService;
import com.example.auth.util.AuthCookieUtil;

import com.example.auth.service.UserService;
import com.example.auth.util.JWTUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Add Slf4j
import com.example.profile.storage.service.ProfileImageService;

import org.springframework.http.HttpStatus;
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
import jakarta.servlet.http.Cookie;
import jakarta.validation.Valid;

//마이페이지 기능을 위한 컨트롤러입니다.
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class MypageController {

    private static final long ACCESS_TOKEN_EXPIRED_MS = 1000 * 60 * 30; // 30분 (ms 단위)
        private final UserService userService;
        private final JWTUtil jwtUtil;
        private final ProfileImageService profileImageService;
        private final RefreshRepository refreshRepository;
        private final AuthCookieUtil authCookieUtil;
        private final PolicyConsentService policyConsentService;

        // 프로필 정보 조회 (GET /mypage) - 수정 없음
        @GetMapping("/mypage")
        public ResponseEntity<ApiResponse> getMyProfile(
                        @AuthenticationPrincipal Long userId) {
                // 인증되지 않은 사용자인 경우 401 반환
                if (userId == null) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(ApiResponse.error("인증이 필요합니다."));
                }

                try {
                        // JWT 토큰에서 ID (userId) 사용
                        // UserService를 통해 실제 DB에서 사용자 정보 조회
                        UserEntity userEntity = userService.findUserById(userId);
                        PolicyConsentService.PolicyConsentStatus policyConsentStatus = policyConsentService
                                        .evaluatePolicyConsentStatus(userId);

                        // Entity를 DTO로 변환
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

                        // 성공 응답 (HTTP 200 OK)
                        log.info("getMyProfile - userId: {}, email: {}, points: {}", userId, userEntity.getEmail(),
                                        userEntity.getCheerPoints());
                        return ResponseEntity.ok(ApiResponse.success("프로필 조회 성공", profileDto));

                } catch (RuntimeException e) {
                        // 토큰은 유효하지만 DB에 유저가 없는 경우 (Zombie Session) -> 401로 응답하여 재로그인 유도
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(ApiResponse.error("요청한 사용자의 프로필 정보를 찾을 수 없습니다. (재로그인 필요)"));

                } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ApiResponse.error("프로필 정보를 불러오는 중 서버 오류가 발생했습니다."));
                }
        }

        // 프로필 정보 수정 (PUT /mypage)
        @PutMapping("/mypage")
        public ResponseEntity<ApiResponse> updateMyProfile(
                        @AuthenticationPrincipal Long userId,
                        @Valid @RequestBody UserProfileDto updateDto) {
                try {
                        // DTO에서 이름 유효성 검증 (@Valid를 사용하므로 간소화)
                        if (updateDto.getName() == null || updateDto.getName().trim().isEmpty()) {
                                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                                .body(ApiResponse.error("이름/닉네임은 필수 입력 항목입니다."));
                        }

                        // 서비스 메서드 호출 시, DTO 객체를 바로 전달
                        UserEntity updatedEntity = userService.updateProfile(
                                        userId,
                                        updateDto);

                        // 유저 정보가 수정되면 즉시 새로운 토큰 생성
                        String newRoleKey = updatedEntity.getRole();
                        String userEmail = updatedEntity.getEmail();
                        Long currentUserId = userId;
                        int tokenVersion = updatedEntity.getTokenVersion() == null ? 0 : updatedEntity.getTokenVersion();

                        String newJwtToken = jwtUtil.createJwt(userEmail, newRoleKey, currentUserId,
                                        ACCESS_TOKEN_EXPIRED_MS, tokenVersion);

                        ResponseCookie cookie = authCookieUtil.buildAuthCookie(newJwtToken, ACCESS_TOKEN_EXPIRED_MS / 1000);

                        // 프로필 정보만 응답 데이터로 전달합니다.
                        Map<String, Object> responseMap = new HashMap<>();

                        // 프론트엔드 MyPage.tsx의 handleSave에서 필요한 필드들
                        responseMap.put("profileImageUrl",
                                        profileImageService.getProfileImageUrl(updatedEntity.getProfileImageUrl()));
                        responseMap.put("name", updatedEntity.getName());
                        responseMap.put("handle", updatedEntity.getHandle());
                        responseMap.put("email", updatedEntity.getEmail());
                        responseMap.put("favoriteTeam",
                                        updatedEntity.getFavoriteTeamId() != null ? updatedEntity.getFavoriteTeamId()
                                                        : "없음");
                        responseMap.put("bio", updatedEntity.getBio());

                        return ResponseEntity.ok()
                                        .header(HttpHeaders.SET_COOKIE, cookie.toString())
                                        .body(ApiResponse.success("프로필 수정 성공 및 JWT 쿠키 재설정 완료", responseMap));

                } catch (RuntimeException e) {
                        // 유효하지 않은 팀 ID 등 RuntimeException 처리
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(ApiResponse.error("프로필 수정 중 오류가 발생했습니다: " + e.getMessage()));
                } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ApiResponse.error("프로필 수정 중 서버 오류가 발생했습니다."));
                }
        }

        /**
         * 비밀번호 변경 (일반 로그인 사용자만)
         */
        @PutMapping("/password")
        public ResponseEntity<ApiResponse> changePassword(
                        @AuthenticationPrincipal Long userId,
                        @Valid @RequestBody com.example.mypage.dto.ChangePasswordRequest request) {
                try {
                        // 새 비밀번호와 확인 일치 체크
                        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
                                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                                .body(ApiResponse.error("새 비밀번호와 비밀번호 확인이 일치하지 않습니다."));
                        }

                        userService.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());

                        return ResponseEntity.ok(ApiResponse.success("비밀번호가 성공적으로 변경되었습니다."));

                } catch (IllegalStateException e) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(ApiResponse.error(e.getMessage()));
                } catch (com.example.common.exception.InvalidCredentialsException e) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(ApiResponse.error("현재 비밀번호가 일치하지 않습니다."));
                } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ApiResponse.error("비밀번호 변경 중 오류가 발생했습니다."));
                }
        }

        /**
         * 계정 삭제 (회원탈퇴)
         */
        @DeleteMapping("/account")
        public ResponseEntity<ApiResponse> deleteAccount(
                        @AuthenticationPrincipal Long userId,
                        @RequestBody(required = false) com.example.mypage.dto.DeleteAccountRequest request) {
                try {
                        String password = request != null ? request.getPassword() : null;

                        userService.deleteAccount(userId, password);

                        // 쿠키 삭제를 위한 빈 쿠키 생성
                        ResponseCookie authCookie = authCookieUtil.buildExpiredAuthCookie();

                        ResponseCookie refreshCookie = authCookieUtil.buildExpiredRefreshCookie();

                        return ResponseEntity.ok()
                                        .header(HttpHeaders.SET_COOKIE, authCookie.toString())
                                        .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                                        .body(ApiResponse.success("계정이 성공적으로 삭제되었습니다."));

                } catch (IllegalArgumentException e) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(ApiResponse.error(e.getMessage()));
                } catch (com.example.common.exception.InvalidCredentialsException e) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(ApiResponse.error("비밀번호가 일치하지 않습니다."));
                } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ApiResponse.error("계정 삭제 중 오류가 발생했습니다."));
                }
        }

        /**
         * 연동된 계정 목록 조회
         */
        @GetMapping("/providers")
        public ResponseEntity<ApiResponse> getConnectedProviders(@AuthenticationPrincipal Long userId) {
                try {
                        java.util.List<com.example.mypage.dto.UserProviderDto> providers = userService
                                        .getConnectedProviders(userId);
                        return ResponseEntity.ok(ApiResponse.success("연동된 계정 목록 조회 성공", providers));
                } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ApiResponse.error("연동된 계정 목록을 불러오는 중 오류가 발생했습니다."));
                }
        }

        /**
         * 계정 연동 해제
         */
        @DeleteMapping("/providers/{provider}")
        public ResponseEntity<ApiResponse> unlinkProvider(
                        @AuthenticationPrincipal Long userId,
                        @PathVariable String provider) {
                try {
                        userService.unlinkProvider(userId, provider);
                        return ResponseEntity.ok(ApiResponse.success("계정 연동이 해제되었습니다."));
                } catch (IllegalStateException e) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(ApiResponse.error(e.getMessage()));
                } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ApiResponse.error("계정 연동 해제 중 오류가 발생했습니다."));
                }
        }

        /**
         * 로그인 기기 목록 조회
         */
        @GetMapping("/sessions")
        public ResponseEntity<ApiResponse> getSessions(
                        @AuthenticationPrincipal Long userId,
                        HttpServletRequest request) {
                try {
                        if (userId == null) {
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                                .body(ApiResponse.error("인증이 필요합니다."));
                        }

                        UserEntity user = userService.findUserById(userId);
                        if (user == null) {
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                                .body(ApiResponse.error("요청한 사용자의 프로필 정보를 찾을 수 없습니다."));
                        }

                        List<RefreshToken> refreshTokens = refreshRepository.findAllByEmailOrderByIdDesc(user.getEmail());
                        String currentRefreshToken = resolveCookieValue(request, "Refresh");
                        String requestUserAgent = request.getHeader("User-Agent");
                        String requestIpAddress = resolveIpAddress(request);

                        List<DeviceSessionDto> sessions = refreshTokens.stream()
                                        .map(token -> buildDeviceSessionDto(token, currentRefreshToken, requestUserAgent, requestIpAddress))
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
                } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ApiResponse.error("기기 목록 조회에 실패했습니다."));
                }
        }

        /**
         * 특정 기기 세션 종료
         */
        @DeleteMapping("/sessions/{sessionId}")
        public ResponseEntity<ApiResponse> deleteSession(
                        @AuthenticationPrincipal Long userId,
                        @PathVariable("sessionId") String sessionId,
                        HttpServletRequest request) {
                try {
                        if (userId == null) {
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                                .body(ApiResponse.error("인증이 필요합니다."));
                        }

                        UserEntity user = userService.findUserById(userId);
                        if (user == null) {
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                                .body(ApiResponse.error("요청한 사용자의 프로필 정보를 찾을 수 없습니다."));
                        }

                        List<RefreshToken> refreshTokens = refreshRepository.findAllByEmailOrderByIdDesc(user.getEmail());
                        String currentRefreshToken = resolveCookieValue(request, "Refresh");
                        String currentSessionId = refreshTokens.stream()
                                        .filter(item -> item.getToken() != null && item.getToken().equals(currentRefreshToken)
                                                        && item.getId() != null)
                                        .map(item -> String.valueOf(item.getId()))
                                        .findFirst()
                                        .orElse(null);
                        RefreshToken targetToken = refreshTokens.stream()
                                        .filter(item -> item.getId() != null && String.valueOf(item.getId()).equals(sessionId))
                                        .findFirst()
                                        .orElse(null);

                        if (targetToken == null || targetToken.getId() == null) {
                                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                                .body(ApiResponse.error("종료할 세션 정보를 찾을 수 없습니다."));
                        }

                        if (Objects.equals(currentSessionId, sessionId) && currentRefreshToken != null) {
                                return ResponseEntity.badRequest()
                                                .body(ApiResponse.error("현재 사용 중인 세션은 직접 종료할 수 없습니다."));
                        }

                        refreshRepository.delete(targetToken);
                        return ResponseEntity.ok(ApiResponse.success("선택한 세션을 종료했습니다."));
                } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ApiResponse.error("세션 종료에 실패했습니다."));
                }
        }

        /**
         * 현재 세션 제외 세션 정리
         */
        @DeleteMapping("/sessions")
                        public ResponseEntity<ApiResponse> deleteSessions(
                        @AuthenticationPrincipal Long userId,
                        @RequestParam(name = "allExceptCurrent", defaultValue = "false") boolean allExceptCurrent,
                        HttpServletRequest request) {
                try {
                if (userId == null) {
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                                .body(ApiResponse.error("인증이 필요합니다."));
                        }

                        if (!allExceptCurrent) {
                                return ResponseEntity.badRequest().body(ApiResponse.error("지원되지 않는 요청입니다."));
                        }

                UserEntity user = userService.findUserById(userId);
                if (user == null) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(ApiResponse.error("요청한 사용자의 프로필 정보를 찾을 수 없습니다."));
                }

                List<RefreshToken> refreshTokens = refreshRepository.findAllByEmailOrderByIdDesc(user.getEmail());
                String currentRefreshToken = resolveCookieValue(request, "Refresh");
                List<RefreshToken> targets = currentRefreshToken == null
                                ? refreshTokens
                                : refreshRepository.findAllByEmailAndTokenNot(user.getEmail(), currentRefreshToken);

                if (targets.isEmpty()) {
                        return ResponseEntity.ok(ApiResponse.success("종료할 다른 세션이 없습니다."));
                }

                refreshRepository.deleteAll(targets);
                return ResponseEntity.ok(ApiResponse.success("현재 기기 제외 다른 기기 로그아웃이 완료되었습니다."));
                } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ApiResponse.error("세션 정리 요청에 실패했습니다."));
                }
        }

        private String resolveCookieValue(HttpServletRequest request, String cookieName) {
                Cookie[] cookies = request.getCookies();
                if (cookies == null || cookieName == null) {
                        return null;
                }

                for (Cookie cookie : cookies) {
                        if (cookieName.equals(cookie.getName())) {
                                return cookie.getValue();
                        }
                }

                return null;
        }

        private String resolveIpAddress(HttpServletRequest request) {
                String xff = request.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isBlank()) {
                        return xff.split(",")[0].trim();
                }

                String realIp = request.getHeader("X-Real-IP");
                if (realIp != null && !realIp.isBlank()) {
                        return realIp.trim();
                }

                String remoteAddr = request.getRemoteAddr();
                return remoteAddr != null ? remoteAddr : "unknown";
        }

        private DeviceSessionDto buildDeviceSessionDto(RefreshToken refreshToken, String currentRefreshToken, String requestUserAgent,
                        String requestIpAddress) {
                String userAgent = requestUserAgent;
                boolean isExpired = refreshToken.getExpiryDate() == null
                                || refreshToken.getExpiryDate().isBefore(LocalDateTime.now());
                boolean isCurrentSession = refreshToken.getToken() != null && currentRefreshToken != null
                                ? refreshToken.getToken().equals(currentRefreshToken) && !isExpired
                                : false;

                String sessionId = refreshToken.getId() != null
                                ? refreshToken.getId().toString()
                                : String.valueOf(Math.abs(Objects.hash(refreshToken.getEmail(), refreshToken.getToken())));

                String deviceType = normalizeText(refreshToken.getDeviceType(), "desktop");
                String deviceLabel = normalizeText(refreshToken.getDeviceLabel(), (isCurrentSession ? resolveDeviceLabel(userAgent, deviceType)
                                : null));
                String browser = normalizeText(refreshToken.getBrowser(), (isCurrentSession ? resolveBrowser(userAgent) : "Unknown"));
                String os = normalizeText(refreshToken.getOs(), (isCurrentSession ? resolveOs(userAgent) : "Unknown"));
                String ipAddress = normalizeText(refreshToken.getIp(), (isCurrentSession ? requestIpAddress : null));

                if (deviceLabel == null) {
                        deviceLabel = isCurrentSession ? resolveDeviceLabel(userAgent, deviceType) : "알 수 없는 기기";
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

        private String normalizeText(String value, String fallback) {
                return (value != null && !value.isBlank()) ? value : fallback;
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

        private String resolveDeviceType(String userAgent) {
                if (userAgent == null) {
                        return "desktop";
                }

                String ua = userAgent.toLowerCase();
                if (ua.contains("mobile") || ua.contains("iphone") || ua.contains("android")) {
                        return "mobile";
                }
                if (ua.contains("ipad") || ua.contains("tablet")) {
                        return "tablet";
                }

                return "desktop";
        }

        private String resolveDeviceLabel(String userAgent, String deviceType) {
                if (userAgent == null || userAgent.isBlank()) {
                        return "알 수 없는 기기";
                }

                String ua = userAgent.toLowerCase();
                if (ua.contains("iphone")) {
                        return "iPhone";
                }
                if (ua.contains("ipad")) {
                        return "iPad";
                }
                if (ua.contains("android")) {
                        return "Android";
                }
                if (ua.contains("windows")) {
                        return "Windows PC";
                }
                if (ua.contains("macintosh") || ua.contains("mac os")) {
                        return "Mac";
                }
                if (ua.contains("linux")) {
                        return "Linux PC";
                }

                return deviceType.equals("mobile") ? "모바일 기기" : "데스크톱";
        }

        private String resolveBrowser(String userAgent) {
                if (userAgent == null) {
                        return "Unknown";
                }

                String ua = userAgent.toLowerCase();
                if (ua.contains("edg/") || ua.contains("edge/")) {
                        return "Microsoft Edge";
                }
                if (ua.contains("chrome/")) {
                        return "Chrome";
                }
                if (ua.contains("firefox/")) {
                        return "Firefox";
                }
                if (ua.contains("safari/") && !ua.contains("chrome/")) {
                        return "Safari";
                }
                if (ua.contains("opera/") || ua.contains("opr/")) {
                        return "Opera";
                }

                return "Unknown";
        }

        private String resolveOs(String userAgent) {
                if (userAgent == null) {
                        return "Unknown";
                }

                String ua = userAgent.toLowerCase();
                if (ua.contains("windows")) {
                        return "Windows";
                }
                if (ua.contains("mac os") || ua.contains("macintosh")) {
                        return "macOS";
                }
                if (ua.contains("android")) {
                        return "Android";
                }
                if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod")) {
                        return "iOS";
                }
                if (ua.contains("linux")) {
                        return "Linux";
                }

                return "Unknown";
        }

}
