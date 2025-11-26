package com.example.demo.Oauth2;

import com.example.demo.dto.CustomOAuth2User;
import com.example.demo.entity.RefreshToken;
import com.example.demo.jwt.JWTUtil;
import com.example.demo.repo.RefreshRepository;
import com.example.demo.entity.UserEntity;
import com.example.demo.repo.UserRepository;

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

@Component
public class CustomSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;
    private final UserRepository userRepository; 

    public CustomSuccessHandler(JWTUtil jwtUtil, RefreshRepository refreshRepository, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.refreshRepository = refreshRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
        String userEmail = (String) principal.getAttributes().get("email"); 
        
        if (userEmail == null || userEmail.isEmpty()) {
            userEmail = principal.getUsername(); 
        }
        
        if (userEmail == null || userEmail.isEmpty()) {
            getRedirectStrategy().sendRedirect(request, response, "http://localhost:3000/login?error=email_missing");
            return;
        }
        
        Optional<UserEntity> userEntityOptional = userRepository.findByEmail(userEmail); 
        
        if (userEntityOptional.isEmpty()) {
            getRedirectStrategy().sendRedirect(request, response, "http://localhost:3000/login?error=user_not_found");
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
        int accessTokenMaxAge = (int)(accessTokenExpiredMs / 1000);
        addSameSiteCookie(response, "Authorization", accessToken, accessTokenMaxAge);
        
        int refreshTokenMaxAge = (int)(jwtUtil.getRefreshTokenExpirationTime() / 1000); 
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
            "http://localhost:3000/oauth/callback?email=%s&name=%s&role=%s&profileImageUrl=%s&favoriteTeam=%s",
            encodedEmail, encodedName, encodedRole, encodedProfileUrl, encodedFavoriteTeam
        );
        
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);

    }
    
    private void addSameSiteCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        String cookieString = String.format("%s=%s; Max-Age=%d; Path=/; HttpOnly; SameSite=Lax", 
                                            name, value, maxAgeSeconds);
        response.addHeader("Set-Cookie", cookieString);
    }
}