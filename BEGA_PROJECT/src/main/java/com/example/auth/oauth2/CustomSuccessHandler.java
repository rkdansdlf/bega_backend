package com.example.auth.oauth2;

import com.example.auth.dto.CustomOAuth2User;
import com.example.auth.entity.RefreshToken;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;

@Component
public class CustomSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;
    private final UserRepository userRepository;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public CustomSuccessHandler(JWTUtil jwtUtil, RefreshRepository refreshRepository, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.refreshRepository = refreshRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
        String userEmail = (String) principal.getAttributes().get("email");

        if (userEmail == null || userEmail.isEmpty()) {
            userEmail = principal.getUsername();
        }

        if (userEmail == null || userEmail.isEmpty()) {
            getRedirectStrategy().sendRedirect(request, response,
                    frontendUrl + "/login?error=email_missing");
            return;
        }

        Optional<UserEntity> userEntityOptional = userRepository.findByEmail(userEmail);

        if (userEntityOptional.isEmpty()) {
            getRedirectStrategy().sendRedirect(request, response,
                    frontendUrl + "/login?error=user_not_found");
            return;
        }

        UserEntity userEntity = userEntityOptional.get();
        String role = userEntity.getRole();
        String userName = userEntity.getName();
        Long userId = userEntity.getId();
        String profileImageUrl = userEntity.getProfileImageUrl();

        // ✅ 수정: getFavoriteTeamId() 사용 (String 반환)
        String favoriteTeamId = userEntity.getFavoriteTeamId();

        // ✅ null이면 "없음"으로 설정
        if (favoriteTeamId == null || favoriteTeamId.isEmpty()) {
            favoriteTeamId = "없음";
        }

        // --- 계정 연동 모드 체크 ---
        // 세션 확인 (CookieAuthorizationRequestRepository -> CustomOAuth2UserService ->
        // 여기서도 확인 가능하도록 세션 유지 필요)
        // 주의: CustomOAuth2UserService에서 이미 한번 확인하고 처리했지만,
        // 여기서도 리다이렉트 분기를 위해 확인이 필요하다면 세션에서 값을 가져와야 함.
        // 다만 CustomOAuth2UserService에서 처리가 끝나면 세션 속성을 지우도록 로직을 짰다면 여기서 확인이 안 될 수 있음.
        // 따라서 CustomOAuth2UserService에서 성공 시 'oauth2_link_success' 같은 플래그를 request 속성에
        // 담는 것이 더 안전함.
        // 하지만 간단하게 세션 값을 CustomOAuth2UserService에서 지우지 않도록 하고, 여기서 지우는 방식으로 변경하거나
        // 혹은 CustomSuccessHandler가 먼저 실행되지 않으므로(UserRequest -> Provider -> Service ->
        // SuccessHandler 순),
        // Service에서 처리 후 SuccessHandler로 넘어옴.

        // 전략 수정: CustomOAuth2UserService에서는 로직만 수행하고,
        // SuccessHandler에서 최종 리다이렉트를 결정하기 위해 세션 값을 확인하고 여기서 삭제함.

        jakarta.servlet.http.HttpSession session = request.getSession(false);
        String linkMode = (session != null) ? (String) session.getAttribute("oauth2_link_mode") : null;
        boolean isLinkMode = "link".equals(linkMode);

        if (isLinkMode) {
            // 연동 모드일 경우: 토큰 발급/쿠키 갱신 없이 마이페이지로 리턴 (기존 세션 유지)
            System.out.println("Processing Account Link Success (Skipping Token Generation)");

            // 세션 정리
            if (session != null) {
                session.removeAttribute("oauth2_link_mode");
                session.removeAttribute("oauth2_link_user_id");
            }

            getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/mypage?status=linked");
            return;
        }
        // -----------------------

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

        // ✅ 사용자 정보를 쿼리 파라미터로 전달
        String encodedEmail = URLEncoder.encode(userEmail, StandardCharsets.UTF_8);
        String encodedName = URLEncoder.encode(userName, StandardCharsets.UTF_8);
        String encodedRole = URLEncoder.encode(role, StandardCharsets.UTF_8);
        String encodedProfileUrl = profileImageUrl != null
                ? URLEncoder.encode(profileImageUrl, StandardCharsets.UTF_8)
                : "";
        String encodedFavoriteTeam = URLEncoder.encode(favoriteTeamId, StandardCharsets.UTF_8);

        String redirectUrl = String.format(
                frontendUrl + "/oauth/callback?email=%s&name=%s&role=%s&profileImageUrl=%s&favoriteTeam=%s",
                encodedEmail, encodedName, encodedRole, encodedProfileUrl, encodedFavoriteTeam);

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);

    }

    private void addSameSiteCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        String cookieString = String.format("%s=%s; Max-Age=%d; Path=/; HttpOnly; Secure; SameSite=None",
                name, value, maxAgeSeconds);
        response.addHeader("Set-Cookie", cookieString);
    }
}