package com.example.auth.oauth2;

import com.example.auth.dto.CustomOAuth2User;
import com.example.auth.entity.RefreshToken;
import com.example.auth.service.OAuth2StateService;
import com.example.auth.util.JWTUtil;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;

@Component
@lombok.extern.slf4j.Slf4j
public class CustomSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;
    private final UserRepository userRepository;
    private final com.example.auth.service.UserService userService; // Inject UserService
    private final OAuth2StateService oAuth2StateService;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.cookie.secure:false}")
    private boolean secureCookie;

    public CustomSuccessHandler(JWTUtil jwtUtil, RefreshRepository refreshRepository, UserRepository userRepository,
            @org.springframework.context.annotation.Lazy com.example.auth.service.UserService userService,
            OAuth2StateService oAuth2StateService) {
        this.jwtUtil = jwtUtil;
        this.refreshRepository = refreshRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.oAuth2StateService = oAuth2StateService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();

        // Use the UserDto directly from the authenticated principal
        // This ensures we use the LINKED account's info, not just the provider's email
        com.example.auth.dto.UserDto userDto = principal.getUserDto();

        String userEmail = userDto.getEmail();
        String role = userDto.getRole();
        String userName = userDto.getName();
        Long userId = userDto.getId();

        // Fetch fresh entity for bonus check / entity operations if needed,
        // using ID which is stable (email might change but ID is PK)
        Optional<UserEntity> userEntityOptional = userRepository.findById(userId);

        if (userEntityOptional.isEmpty()) {
            // Should not happen if CustomOAuth2UserService did its job
            getRedirectStrategy().sendRedirect(request, response,
                    frontendUrl + "/login?error=user_not_found_after_auth");
            return;
        }

        UserEntity userEntity = userEntityOptional.get();
        String profileImageUrl = userEntity.getProfileImageUrl();
        String userHandle = userEntity.getHandle();

        // ✅ 수정: getFavoriteTeamId() 사용 (String 반환)
        String favoriteTeamId = userEntity.getFavoriteTeamId();

        // ✅ null이면 "없음"으로 설정
        if (favoriteTeamId == null || favoriteTeamId.isEmpty()) {
            favoriteTeamId = "없음";
        }

        // --- 계정 연동 모드 체크 ---
        // state 파라미터에서 연동 모드 확인 (| 포함 여부)
        boolean isLinkMode = checkLinkModeFromState(request);

        if (isLinkMode) {
            // [Security Fix] 연동 모드일 경우에도 새 토큰 발급 (보안 강화)
            // 기존 토큰이 만료되었을 수 있고, 연동 후 권한이 변경되었을 수 있음
            log.info("Processing Account Link Success (Refreshing Tokens)");

            // 새 토큰 발급
            long accessTokenExpiredMs = 1000 * 60 * 60 * 2L; // 2시간
            String accessToken = jwtUtil.createJwt(userEmail, role, userId, accessTokenExpiredMs);
            String refreshToken = jwtUtil.createRefreshToken(userEmail, role, userId);

            // Refresh Token DB 저장/갱신
            RefreshToken existToken = refreshRepository.findByEmail(userEmail);
            if (existToken == null) {
                RefreshToken newRefreshToken = new RefreshToken();
                newRefreshToken.setEmail(userEmail);
                newRefreshToken.setToken(refreshToken);
                newRefreshToken.setExpiryDate(LocalDateTime.now().plusWeeks(1));
                refreshRepository.save(newRefreshToken);
            } else {
                existToken.setToken(refreshToken);
                existToken.setExpiryDate(LocalDateTime.now().plusWeeks(1));
                refreshRepository.save(existToken);
            }

            // 쿠키에 토큰 저장
            int accessTokenMaxAge = (int) (accessTokenExpiredMs / 1000);
            addSameSiteCookie(response, "Authorization", accessToken, accessTokenMaxAge);
            int refreshTokenMaxAge = (int) (jwtUtil.getRefreshTokenExpirationTime() / 1000);
            addSameSiteCookie(response, "Refresh", refreshToken, refreshTokenMaxAge);

            // [Security Fix] State 데이터 저장 - userId만 Redis에 저장 (민감 정보 최소화)
            String stateId = oAuth2StateService.saveState(userId);
            String redirectUrl = frontendUrl + "/oauth/callback?state=" + stateId + "&status=linked";
            getRedirectStrategy().sendRedirect(request, response, redirectUrl);
            return;
        }
        // -----------------------

        // 일일 출석 보너스 체크 (OAuth2 로그인 시)
        userService.checkAndApplyDailyLoginBonus(userEntity);

        // Access Token 생성
        long accessTokenExpiredMs = 1000 * 60 * 60 * 2L; // 2시간
        String accessToken = jwtUtil.createJwt(userEmail, role, userId, accessTokenExpiredMs);

        // Refresh Token 생성
        String refreshToken = jwtUtil.createRefreshToken(userEmail, role, userId);

        // Refresh Token DB 저장
        RefreshToken existToken = refreshRepository.findByEmail(userEmail);

        if (existToken == null) {
            RefreshToken newRefreshToken = new RefreshToken();
            newRefreshToken.setEmail(userEmail);
            newRefreshToken.setToken(refreshToken);
            newRefreshToken.setExpiryDate(LocalDateTime.now().plusWeeks(1));
            refreshRepository.save(newRefreshToken);
        } else {
            existToken.setToken(refreshToken);
            existToken.setExpiryDate(LocalDateTime.now().plusWeeks(1));
            refreshRepository.save(existToken);
        }

        // 쿠키에 토큰 저장
        int accessTokenMaxAge = (int) (accessTokenExpiredMs / 1000);
        addSameSiteCookie(response, "Authorization", accessToken, accessTokenMaxAge);

        int refreshTokenMaxAge = (int) (jwtUtil.getRefreshTokenExpirationTime() / 1000);
        addSameSiteCookie(response, "Refresh", refreshToken, refreshTokenMaxAge);

        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }

        // [Security Fix] 사용자 정보를 Redis에 저장 - userId만 저장 (민감 정보 최소화)
        String stateId = oAuth2StateService.saveState(userId);
        String redirectUrl = frontendUrl + "/oauth/callback?state=" + stateId;
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);

    }

    private void addSameSiteCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        // [Security Fix] 프로덕션 환경에서는 Secure 플래그 추가
        String secureFlag = secureCookie ? "; Secure" : "";
        String cookieString = String.format("%s=%s; Max-Age=%d; Path=/; HttpOnly; SameSite=Lax%s",
                name, value, maxAgeSeconds, secureFlag);
        response.addHeader("Set-Cookie", cookieString);
    }

    /**
     * state 파라미터에서 연동 모드 여부 확인
     * state에 | 가 포함되어 있으면 연동 모드
     */
    private boolean checkLinkModeFromState(HttpServletRequest request) {
        String state = request.getParameter("state");
        if (state == null) {
            return false;
        }
        return state.contains("|");
    }
}