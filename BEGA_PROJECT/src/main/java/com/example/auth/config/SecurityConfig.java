package com.example.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.example.auth.oauth2.CustomOAuth2UserService;
import com.example.auth.oauth2.CustomSuccessHandler;
import com.example.auth.oauth2.CookieAuthorizationRequestRepository;
import com.example.auth.filter.JWTFilter;
import com.example.auth.util.JWTUtil;

import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

        // ========================================
        // 공개 엔드포인트 그룹 정의
        // ========================================

        /** 인증 관련 공개 엔드포인트 */
        private static final String[] PUBLIC_AUTH_ENDPOINTS = {
                "/api/auth/login",
                "/api/auth/signup",
                "/api/auth/reissue",
                "/api/auth/oauth2/state/**",
                "/api/auth/password-reset/request",
                "/api/auth/password-reset/confirm",
                "/oauth2/**",
                "/login/**",
                "/error"
        };

        /** 테스트 및 시스템 엔드포인트 */
        private static final String[] PUBLIC_SYSTEM_ENDPOINTS = {
                "/api/test/**",
                "/actuator/health",
                "/ws/**"
        };

        /** 공개 API 엔드포인트 (인증 불필요) */
        private static final String[] PUBLIC_API_ENDPOINTS = {
                "/api/stadiums/**",
                "/api/places/**",
                "/api/teams/**",
                "/api/games/**",
                "/api/parties/**",
                "/api/applications/**",
                "/api/chat/**",
                "/api/checkin/**",
                "/api/users/email-to-id",
                "/api/kbo/league-start-dates",
                "/api/kbo/schedule/**",
                "/api/kbo/rankings/**",
                "/api/kbo/offseason/**",
                "/api/matches/**",
                "/api/diary/public/**"
        };

        /** 공개 GET 요청 엔드포인트 */
        private static final String[] PUBLIC_GET_ENDPOINTS = {
                "/api/cheer/posts",
                "/api/cheer/posts/**",
                "/api/cheer/user/**",
                "/api/users/*/profile",
                "/api/diary/games",
                "/api/games/past",
                "/api/predictions/status/**",
                "/api/predictions/ranking/current-season",
                "/api/predictions/ranking/share/**",
                "/api/reviews/party/**",
                "/api/reviews/user/*/average"
        };

        /** 인증 필수 엔드포인트 */
        private static final String[] AUTHENTICATED_ENDPOINTS = {
                "/api/auth/link-token",
                "/api/diary/**",
                "/api/predictions/**"
        };

        /** 관리자 전용 엔드포인트 */
        private static final String[] ADMIN_ENDPOINTS = {
                "/api/admin/**",
                "/admin/**"
        };

        // ========================================

        private final CustomOAuth2UserService customOAuth2UserService;
        private final CustomSuccessHandler customSuccessHandler;
        private final JWTUtil jwtUtil;
        private final CookieAuthorizationRequestRepository cookieauthorizationrequestRepository;
        private final com.example.auth.service.TokenBlacklistService tokenBlacklistService;

        @org.springframework.beans.factory.annotation.Value("${app.allowed-origins:http://localhost:3000,http://localhost:8080}")
        private String allowedOriginsStr;

        public SecurityConfig(CustomOAuth2UserService customOAuth2UserService,
                        CustomSuccessHandler customSuccessHandler, JWTUtil jwtUtil,
                        CookieAuthorizationRequestRepository cookieauthorizationrequestRepository,
                        com.example.auth.service.TokenBlacklistService tokenBlacklistService) {

                this.customOAuth2UserService = customOAuth2UserService;
                this.customSuccessHandler = customSuccessHandler;
                this.jwtUtil = jwtUtil;
                this.cookieauthorizationrequestRepository = cookieauthorizationrequestRepository;
                this.tokenBlacklistService = tokenBlacklistService;
        }

        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
                return configuration.getAuthenticationManager();
        }

        @Bean
        public BCryptPasswordEncoder bCryptPasswordEncoder() {
                return new BCryptPasswordEncoder();
        }

        /**
         * 역할 계층 설정: SUPER_ADMIN > ADMIN > USER
         * SUPER_ADMIN은 모든 권한을 가지며, 권한 관리 기능에 접근할 수 있습니다.
         * ADMIN은 일반 관리 기능에 접근할 수 있습니다.
         */
        @Bean
        public org.springframework.security.access.hierarchicalroles.RoleHierarchy roleHierarchy() {
                return org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl
                                .withDefaultRolePrefix()
                                .role("SUPER_ADMIN").implies("ADMIN")
                                .role("ADMIN").implies("USER")
                                .build();
        }

        // CORS Configuration Source Bean 정의
        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();

                configuration.setAllowedOriginPatterns(
                                Arrays.asList(allowedOriginsStr.split(",")));
                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
                configuration.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type"));
                configuration.setAllowCredentials(true);
                configuration.setMaxAge(3600L);

                // JWT Cookie를 Set-Cookie 헤더에 노출하도록 설정 유지
                configuration.setExposedHeaders(Arrays.asList("Authorization", "Set-Cookie"));

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);

                return source;
        }

        @Bean
        public JWTFilter jwtFilter(org.springframework.core.env.Environment env) {
                boolean isDev = Arrays.asList(env.getActiveProfiles()).contains("dev");
                List<String> origins = Arrays.asList(allowedOriginsStr.split(","));
                return new JWTFilter(jwtUtil, isDev, origins, tokenBlacklistService);
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http, JWTFilter jwtFilter) throws Exception {

                // CORS 활성화 및 CSRF 비활성화
                http
                                .cors((cors) -> cors.configurationSource(corsConfigurationSource()));

                http
                                .csrf((auth) -> auth.disable());

                // From 로그인 방식 disable
                http
                                .formLogin((auth) -> auth.disable());

                // HTTP Basic 인증 방식 disable
                http
                                .httpBasic((auth) -> auth.disable());

                // 인자로 받은 jwtFilter를 사용
                http
                                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

                // OAuth2 설정
                http
                                .oauth2Login((oauth2) -> oauth2
                                                .userInfoEndpoint((userInfoEndpointConfig) -> userInfoEndpointConfig
                                                                .userService(customOAuth2UserService))
                                                .successHandler(customSuccessHandler)
                                                .failureHandler((request, response, exception) -> {
                                                        response.sendRedirect("/login?error=" + exception.getMessage());
                                                })
                                                .authorizationEndpoint(authorization -> authorization
                                                                .authorizationRequestRepository(
                                                                                cookieauthorizationrequestRepository)));

                // ========================================
                // 경로별 인가 설정 (그룹화된 상수 사용)
                // ========================================
                http
                                .authorizeHttpRequests((auth) -> auth
                                                // 1. OPTIONS 요청 허용 (CORS Preflight)
                                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                                                // 2. 인증 관련 공개 엔드포인트
                                                .requestMatchers(PUBLIC_AUTH_ENDPOINTS).permitAll()
                                                .requestMatchers("/api/auth/**").permitAll() // 나머지 auth 경로
                                                .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()

                                                // 3. 시스템/테스트 엔드포인트
                                                .requestMatchers(PUBLIC_SYSTEM_ENDPOINTS).permitAll()
                                                .requestMatchers("/actuator/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                                                // 4. 관리자 전용 엔드포인트
                                                .requestMatchers(ADMIN_ENDPOINTS).hasAnyRole("ADMIN", "SUPER_ADMIN")

                                                // 5. 인증 필수 엔드포인트 (순서 중요: 구체적 경로 먼저)
                                                .requestMatchers("/api/auth/link-token").authenticated()
                                                .requestMatchers(HttpMethod.GET, "/api/cheer/posts/following").authenticated()
                                                .requestMatchers(HttpMethod.GET, "/api/diary/games").permitAll()
                                                .requestMatchers("/api/diary/**").authenticated()
                                                .requestMatchers(HttpMethod.POST, "/api/predictions/vote").authenticated()
                                                .requestMatchers("/api/predictions/**").authenticated()

                                                // 6. 공개 GET 요청 엔드포인트
                                                .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()

                                                // 7. 공개 API 엔드포인트
                                                .requestMatchers(PUBLIC_API_ENDPOINTS).permitAll()

                                                // 8. 나머지 모든 요청은 인증 필요
                                                .anyRequest().authenticated())

                                // 302 리다이렉션 방지: 인증 실패 시 /login으로 리다이렉트 대신 401 응답 반환
                                .exceptionHandling((exceptionHandling) -> exceptionHandling
                                                .authenticationEntryPoint((request, response, authException) -> {
                                                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                                        response.setContentType("application/json;charset=UTF-8");

                                                        String jsonResponse = "{\"success\":false,\"message\":\"인증이 필요합니다.\",\"error\":\"Unauthorized\"}";
                                                        response.getWriter().write(jsonResponse);
                                                }));

                // jwt 기반 인증처리니 세션을 stateless로 설정
                http
                                .sessionManagement((session) -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

                return http.build();
        }
}