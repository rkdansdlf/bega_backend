package com.example.auth.filter;

import com.example.auth.service.TokenBlacklistService;
import com.example.auth.repository.UserRepository;
import com.example.auth.entity.UserEntity;
import com.example.auth.util.JWTUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JWTFilterTokenTypeTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private UserRepository userRepository;

    private JWTUtil jwtUtil;
    private JWTFilter jwtFilter;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        jwtUtil = new JWTUtil(SECRET, 1000L * 60 * 60 * 24 * 7);
        jwtUtil.validateSecret();
        jwtFilter = new JWTFilter(jwtUtil, false, List.of("http://localhost:5173"), tokenBlacklistService,
                userRepository);
        secretKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

        Mockito.lenient().when(tokenBlacklistService.isBlacklisted(anyString())).thenReturn(false);
        Mockito.lenient().when(userRepository.findById(Mockito.anyLong())).thenReturn(Optional.of(
                UserEntity.builder()
                        .id(1L)
                        .enabled(true)
                        .locked(false)
                        .tokenVersion(0)
                        .role("ROLE_USER")
                        .build()
        ));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("access token은 인증 컨텍스트에 등록된다")
    void accessToken_isAcceptedForAuthentication() throws Exception {
        String accessToken = jwtUtil.createJwt("user@test.com", "ROLE_USER", 1L, 60_000L);

        executeFilter(accessToken);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(1L);
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("link token은 인증에 사용되지 않는다")
    void linkToken_isRejectedForAuthentication() throws Exception {
        String linkToken = jwtUtil.createLinkToken(1L, 60_000L);

        executeFilter(linkToken);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("refresh token은 인증에 사용되지 않는다")
    void refreshToken_isRejectedForAuthentication() throws Exception {
        String refreshToken = jwtUtil.createRefreshToken("user@test.com", "ROLE_USER", 1L);

        executeFilter(refreshToken);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("token_type이 없는 레거시 토큰도 인증에 사용된다")
    void legacyTokenWithoutType_isAcceptedForAuthentication() throws Exception {
        String legacyToken = Jwts.builder()
                .claim("email", "legacy@test.com")
                .claim("role", "ROLE_USER")
                .claim("user_id", 1L)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(secretKey)
                .compact();

        executeFilter(legacyToken);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(1L);
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("access 타입이라도 LINK_MODE role이면 인증에서 차단된다")
    void accessTypeWithLinkModeRole_isRejectedForAuthentication() throws Exception {
        String legacyLinkToken = Jwts.builder()
                .claim("token_type", "access")
                .claim("email", "link-action")
                .claim("role", "LINK_MODE")
                .claim("user_id", 99L)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(secretKey)
                .compact();

        executeFilter(legacyLinkToken);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("/api/auth/login 경로는 CSRF 검증 없이 필터 체인을 통과한다")
    void authLoginPath_isNotBlockedByCsrfCheck() throws Exception {
        String accessToken = jwtUtil.createJwt("user@test.com", "ROLE_USER", 1L, 60_000L);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader("Referer", "http://evil.test/login");
        request.addHeader("Origin", "http://evil.test");
        request.addHeader("Authorization", "Bearer " + accessToken);

        DummyFilterChain chain = new DummyFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtFilter.doFilter(request, response, chain);

        assertThat(chain.invoked).isTrue();
        assertThat(response.getStatus()).isNotEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    @DisplayName("/api/auth/login/ 경로도 동일하게 필터 스킵 대상이다")
    void authLoginPathWithTrailingSlash_isNotBlockedByCsrfCheck() throws Exception {
        String accessToken = jwtUtil.createJwt("user@test.com", "ROLE_USER", 1L, 60_000L);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login/");
        request.addHeader("Referer", "http://evil.test/login");
        request.addHeader("Origin", "http://evil.test");
        request.addHeader("Authorization", "Bearer " + accessToken);

        DummyFilterChain chain = new DummyFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtFilter.doFilter(request, response, chain);

        assertThat(chain.invoked).isTrue();
        assertThat(response.getStatus()).isNotEqualTo(HttpStatus.FORBIDDEN.value());
    }

    private void executeFilter(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected/resource");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtFilter.doFilter(request, response, chain);
    }

    private static class DummyFilterChain implements FilterChain {
        private boolean invoked;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            invoked = true;
        }
    }
}
