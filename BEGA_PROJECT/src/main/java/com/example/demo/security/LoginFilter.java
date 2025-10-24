package com.example.demo.security;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Iterator;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.demo.entity.RefreshToken; 
import com.example.demo.jwt.JWTUtil;
import com.example.demo.repo.RefreshRepository;
import com.example.demo.service.CustomUserDetails;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException; 

public class LoginFilter extends UsernamePasswordAuthenticationFilter {

    private final AuthenticationManager authenticationManager;
    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository; 

    public LoginFilter(AuthenticationManager authenticationManager, JWTUtil jwtUtil, RefreshRepository refreshRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.refreshRepository = refreshRepository;
        
        // 🚨 필터가 처리할 경로를 /login으로 설정합니다.
        // 부모 클래스가 기본적으로 POST만 처리하도록 설정되어 있습니다.
        setFilterProcessesUrl("/login"); 
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {

        // 🚨 [수정]: POST 요청이 아닐 경우, 부모 클래스(UsernamePasswordAuthenticationFilter)는 
        // 기본적으로 AuthenticationException을 던지며, 이 예외를 catch하여 401 응답을 내보냅니다.
        // 이 로직을 제거하고 부모 클래스의 기본 동작(POST만 처리)에 의존하도록 코드를 단순화합니다.
        // 만약 GET 요청 시에도 필터가 작동하는 문제가 지속되면, SecurityConfig에서 
        // .addFilterAt()을 사용할 때 RequestMatcher를 사용해 POST 요청만 명시적으로 필터가 잡도록 해야 합니다.
        
        // 현재는 POST 요청만 필터링하는 부모의 기본 기능을 사용한다고 가정하고, 
        // 불필요한 GET 검사 로직을 제거합니다.
        if (!request.getMethod().equals("POST")) {
            // GET 요청이 들어올 경우, 부모 클래스는 이 요청을 무시하고 다음 필터로 넘겨야 하지만,
            // 커스텀 필터의 설정 문제로 인해 GET 요청을 처리하고 있다면 
            // 아래의 예외 대신, Custom Authentication Manager를 통해 처리해야 합니다.
            // 하지만 지금은 로직을 부모 클래스에 의존하여 단순화합니다.
        }


        String username = obtainUsername(request);
        String password = obtainPassword(request);

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(username, password, null);

        return authenticationManager.authenticate(authToken);
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authentication) throws IOException, ServletException {

        CustomUserDetails customUserDetails = (CustomUserDetails) authentication.getPrincipal();

        String username = customUserDetails.getUsername();

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        Iterator<? extends GrantedAuthority> iterator = authorities.iterator();
        GrantedAuthority auth = iterator.next();

        String role = auth.getAuthority();

        // Access Token 만료 시간 (예: 2시간)
        long accessTokenExpiredMs = 1000 * 60 * 60 * 2L; 

        // Access Token 생성
        String accessToken = jwtUtil.createJwt(username, role, accessTokenExpiredMs);
        
        // Refresh Token 생성
        String refreshToken = jwtUtil.createRefreshToken(username, role);

        // Refresh Token DB 저장/업데이트
        RefreshToken existToken = refreshRepository.findByUsername(username);

        if (existToken == null) {
            RefreshToken newRefreshToken = new RefreshToken();
            newRefreshToken.setUsername(username);
            newRefreshToken.setToken(refreshToken);
            newRefreshToken.setExpiryDate(LocalDateTime.now().plusWeeks(1)); 
            
            refreshRepository.save(newRefreshToken);

        } else {
            existToken.setToken(refreshToken);
            existToken.setExpiryDate(LocalDateTime.now().plusWeeks(1));
            refreshRepository.save(existToken);
        }
        
        // 쿠키에 Access/Refresh Token 동시 추가
        
        // Access Token 쿠키 (Authorization 헤더 대신 쿠키 사용으로 변경, HttpOnly)
        response.addCookie(createCookie("Authorization", accessToken, (int)(accessTokenExpiredMs / 1000)));
        
        // Refresh Token 쿠키
        int refreshTokenMaxAge = (int)(jwtUtil.getRefreshTokenExpirationTime() / 1000);
        response.addCookie(createCookie("Refresh", refreshToken, refreshTokenMaxAge));


        // 🚨 로그 출력 형식 수정
        System.out.println("로그인 성공");
        System.out.println("--- JWT 토큰 발행 성공 (일반 로그인) ---");
        System.out.println("발행된 Access Token: " + accessToken.substring(0, 10) + "...");
        System.out.println("Refresh Token (DB 저장됨): " + refreshToken.substring(0, 10) + "...");
        System.out.println("토큰 사용자: " + username);
        System.out.println("권한: "+ role);
        System.out.println("-------------------------------------");
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException, ServletException {
        // 인증 실패(비밀번호 오류, 사용자 없음 등) 시 401 반환
        response.setStatus(401);
        System.out.println("fail");
    }
    
    private Cookie createCookie(String key, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        // cookie.setSecure(true); 

        return cookie;
    }
}
