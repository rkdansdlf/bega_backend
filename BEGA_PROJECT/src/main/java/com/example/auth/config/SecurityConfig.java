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
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.example.ai.config.AiServiceSettings;
import com.example.auth.oauth2.CustomOAuth2UserService;
import com.example.auth.oauth2.CustomSuccessHandler;
import com.example.auth.oauth2.CookieAuthorizationRequestRepository;
import com.example.auth.filter.JWTFilter;
import com.example.auth.repository.UserRepository;
import com.example.auth.util.JWTUtil;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.core.env.Environment;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

        private static final AuthenticatedAuthorizationManager<Object> AUTHENTICATED_AUTHORIZATION_MANAGER = AuthenticatedAuthorizationManager
                        .authenticated();

        // ========================================

        private static final List<String> DEFAULT_ALLOWED_ORIGINS = List.of(
                        "http://localhost",
                        "http://localhost:3000",
                        "http://localhost:5173",
                        "http://localhost:5176",
                        "http://localhost:5177",
                        "http://localhost:8080",
                        "http://host.docker.internal",
                        "http://host.docker.internal:3000",
                        "http://host.docker.internal:5173",
                        "http://host.docker.internal:5176",
                        "http://host.docker.internal:5177",
                        "http://host.docker.internal:8080",
                        "http://127.0.0.1",
                        "http://127.0.0.1:3000",
                        "http://127.0.0.1:5173",
                        "http://127.0.0.1:5176",
                        "http://127.0.0.1:5177",
                        "https://www.begabaseball.xyz",
                        "https://begabaseball.xyz",
                        "https://*.frontend-dfl.pages.dev",
                        "http://[::1]");
        // 공개 엔드포인트 그룹 정의
        // ========================================

        /** 인증 관련 공개 엔드포인트 */
        private static final String[] PUBLIC_AUTH_ENDPOINTS = {
                        "/api/auth/login",
                        "/api/auth/signup",
                        "/api/auth/policies/required",
                        "/api/auth/reissue",
                        "/api/auth/logout",
                        "/api/auth/password/reset/request",
                        "/api/auth/password/reset/confirm",
                        "/api/auth/account/deletion/recovery",
                        "/api/auth/password-reset/request",
                        "/api/auth/password-reset/confirm",
                        "/api/auth/oauth2/state/**",
                        "/oauth2/authorization/**",
                        "/login/oauth2/code/**",
                        "/login",
                        "/error"
        };

        /** 인증 필요 엔드포인트 (auth 하위 경로 일부) */
        private static final String[] PRIVATE_AUTH_ENDPOINTS = {
                        "/api/auth/mypage",
                        "/api/auth/policies/consents",
                        "/api/auth/password",
                        "/api/auth/account",
                        "/api/auth/security-events",
                        "/api/auth/link-token",
                        "/api/auth/sessions",
                        "/api/auth/sessions/**",
                        "/api/auth/trusted-devices",
                        "/api/auth/trusted-devices/*",
                        "/api/auth/providers",
                        "/api/auth/providers/*"
        };

        /** 테스트 및 시스템 엔드포인트 */
        private static final String[] PUBLIC_SYSTEM_ENDPOINTS = {
                        "/actuator/health"
        };

        /** 개발/로컬에서만 공개되는 시스템 엔드포인트 */
        private static final String[] DEV_LOCAL_PUBLIC_SYSTEM_ENDPOINTS = {
                        "/api/test/**",
                        "/actuator/prometheus",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
        };

        /** 공개 API 엔드포인트 (인증 불필요) */
        private static final String[] PUBLIC_API_ENDPOINTS = {
                        "/api/stadiums/**",
                        "/api/places/**",
                        "/api/teams/**",
                        "/api/games/**",
                        "/api/kbo/league-start-dates",
                        "/api/kbo/schedule/**",
                        "/api/kbo/rankings/**",
                        "/api/kbo/offseason/**",
                        "/api/matches/**",
                        "/api/diary/public/**",
                        "/api/client-errors",
                        "/api/client-errors/feedback"
        };

        /** 공개 GET 요청 엔드포인트 */
        private static final String[] PUBLIC_GET_ENDPOINTS = {
                        "/api/cheer/posts",
                        "/api/cheer/posts/**",
                        "/api/cheer/user/**",
                        "/api/users/profile/*",
                        "/api/diary/games",
                        "/api/diary/seat-views",
                        "/api/games/past",
                        "/api/predictions/ranking/current-season",
                        "/api/predictions/ranking/share/**",
                        "/api/users/profile/*/follow-counts",
                        "/api/users/profile/*/followers",
                        "/api/users/profile/*/following",
                        "/api/leaderboard",
                        "/api/leaderboard/hot-streaks",
                        "/api/leaderboard/recent-scores",
                        "/api/leaderboard/stats",
                        "/api/leaderboard/profile/*",
                        "/api/leaderboard/profile/*/rank",
                        "/api/leaderboard/achievements/rare"
        };

        /** 공개 파티 조회 엔드포인트 (my는 제외) */
        private static final String[] PUBLIC_PARTY_GET_ENDPOINTS = {
                        "/api/parties",
                        "/api/parties/search",
                        "/api/parties/status/*",
                        "/api/parties/profile/*",
                        "/api/parties/upcoming"
        };

        /** 관리자 전용 엔드포인트 */
        private static final String[] ADMIN_ENDPOINTS = {
                        "/api/admin/**",
                        "/admin/**",
                        "/dashboard",
                        "/dashboard/**"
        };

        /** AI 프록시 엔드포인트 (인증 필수) */
        private static final String[] ADMIN_AI_ENDPOINTS = {
                        "/api/ai/release-decision/**"
        };

        /** AI 프록시 엔드포인트 (인증 필수) */
        private static final String[] PRIVATE_AI_ENDPOINTS = {
                        "/api/ai/**"
        };

        // ========================================

        private final CustomOAuth2UserService customOAuth2UserService;
        private final CustomSuccessHandler customSuccessHandler;
        private final JWTUtil jwtUtil;
        private final CookieAuthorizationRequestRepository cookieauthorizationrequestRepository;
        private final com.example.auth.service.TokenBlacklistService tokenBlacklistService;
        private final UserRepository userRepository;
        private final com.example.auth.service.AuthSecurityMonitoringService authSecurityMonitoringService;
        private final Environment environment;
        private final AiServiceSettings aiServiceSettings;
        @org.springframework.beans.factory.annotation.Value("${app.ai.proxy.public-in-dev:false}")
        private boolean publicAiProxyInDevEnabled;

        @org.springframework.beans.factory.annotation.Value("${app.allowed-origins:http://localhost,http://localhost:3000,http://localhost:5173,http://localhost:5176,http://localhost:5177,http://localhost:8080,http://host.docker.internal,http://host.docker.internal:3000,http://host.docker.internal:5173,http://host.docker.internal:5176,http://host.docker.internal:5177,http://host.docker.internal:8080,http://127.0.0.1,http://127.0.0.1:3000,http://127.0.0.1:5173,http://127.0.0.1:5176,http://127.0.0.1:5177,https://www.begabaseball.xyz,https://begabaseball.xyz,https://*.frontend-dfl.pages.dev}")
        private String allowedOriginsStr;

        private java.util.List<String> parseAllowedOrigins() {
                List<String> parsed = Arrays
                                .stream(allowedOriginsStr == null ? new String[0] : allowedOriginsStr.split(","))
                                .map(String::trim)
                                .filter(origin -> !origin.isEmpty())
                                .filter(origin -> !origin.equals("*"))
                                .toList();
                if (parsed.isEmpty()) {
                        return DEFAULT_ALLOWED_ORIGINS;
                }

                LinkedHashSet<String> merged = new LinkedHashSet<>(DEFAULT_ALLOWED_ORIGINS);
                merged.addAll(parsed);
                return new ArrayList<>(merged);
        }

        public SecurityConfig(CustomOAuth2UserService customOAuth2UserService,
                        CustomSuccessHandler customSuccessHandler, JWTUtil jwtUtil,
                        CookieAuthorizationRequestRepository cookieauthorizationrequestRepository,
                        com.example.auth.service.TokenBlacklistService tokenBlacklistService,
                        UserRepository userRepository,
                        com.example.auth.service.AuthSecurityMonitoringService authSecurityMonitoringService,
                        Environment environment,
                        AiServiceSettings aiServiceSettings) {

                this.customOAuth2UserService = customOAuth2UserService;
                this.customSuccessHandler = customSuccessHandler;
                this.jwtUtil = jwtUtil;
                this.cookieauthorizationrequestRepository = cookieauthorizationrequestRepository;
                this.tokenBlacklistService = tokenBlacklistService;
                this.userRepository = userRepository;
                this.authSecurityMonitoringService = authSecurityMonitoringService;
                this.environment = environment;
                this.aiServiceSettings = aiServiceSettings;
        }

        private boolean isDevOrLocalProfile() {
                return Arrays.stream(environment.getActiveProfiles())
                                .anyMatch(profile -> "dev".equalsIgnoreCase(profile)
                                                || "local".equalsIgnoreCase(profile));
        }

        boolean isProdProfile() {
                return Arrays.stream(environment.getActiveProfiles())
                                .anyMatch(profile -> "prod".equalsIgnoreCase(profile));
        }

        String[] publicSystemEndpoints() {
                if (!isDevOrLocalProfile()) {
                        return PUBLIC_SYSTEM_ENDPOINTS;
                }

                String[] merged = Arrays.copyOf(
                                PUBLIC_SYSTEM_ENDPOINTS,
                                PUBLIC_SYSTEM_ENDPOINTS.length + DEV_LOCAL_PUBLIC_SYSTEM_ENDPOINTS.length);
                System.arraycopy(
                                DEV_LOCAL_PUBLIC_SYSTEM_ENDPOINTS,
                                0,
                                merged,
                                PUBLIC_SYSTEM_ENDPOINTS.length,
                                DEV_LOCAL_PUBLIC_SYSTEM_ENDPOINTS.length);
                return merged;
        }

        boolean allowUnauthenticatedAiProxy() {
                return publicAiProxyInDevEnabled && isDevOrLocalProfile();
        }

        boolean isAuthenticatedPrincipal(Authentication authentication, Object object) {
                if (authentication == null) {
                        return false;
                }

                AuthorizationDecision decision = AUTHENTICATED_AUTHORIZATION_MANAGER.check(() -> authentication,
                                object);
                return decision != null && decision.isGranted();
        }

        boolean hasValidAiProxyInternalToken(Object object) {
                if (!isDevOrLocalProfile()) {
                        return false;
                }
                if (!(object instanceof RequestAuthorizationContext requestContext)) {
                        return false;
                }

                HttpServletRequest request = requestContext.getRequest();
                if (request == null) {
                        return false;
                }

                String expectedToken = aiServiceSettings.getResolvedInternalToken();
                String providedToken = request.getHeader("X-Internal-Api-Key");
                if (!StringUtils.hasText(expectedToken) || !StringUtils.hasText(providedToken)) {
                        return false;
                }

                return expectedToken.trim().equals(providedToken.trim());
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
                                parseAllowedOrigins());
                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
                configuration.setAllowedHeaders(List.of("*"));
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
                List<String> origins = parseAllowedOrigins();
                return new JWTFilter(
                                jwtUtil,
                                isDev,
                                origins,
                                tokenBlacklistService,
                                userRepository,
                                authSecurityMonitoringService);
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http, JWTFilter jwtFilter) throws Exception {
                final boolean publicAiProxyInDev = allowUnauthenticatedAiProxy();

                // CORS 활성화 및 CSRF 비활성화 (JWT 토큰 기반 인증이므로 CSRF 비활성화)
                http
                                .cors((cors) -> cors.configurationSource(corsConfigurationSource()));

                http
                                .csrf((auth) -> auth.disable());

                // 보안 HTTP 헤더 설정
                http
                                .headers((headers) -> headers
                                                .frameOptions(frame -> frame.deny())
                                                .contentTypeOptions(org.springframework.security.config.Customizer
                                                                .withDefaults())
                                                .xssProtection(org.springframework.security.config.Customizer
                                                                .withDefaults()));

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
                                                        Object oauth2CookieError = request.getAttribute(
                                                                        CookieAuthorizationRequestRepository.OAUTH2_COOKIE_ERROR_ATTRIBUTE);
                                                        if (oauth2CookieError instanceof String) {
                                                                cookieauthorizationrequestRepository
                                                                                .removeAuthorizationRequestCookies(
                                                                                                request, response);
                                                                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                                                                response.setContentType(
                                                                                "application/json;charset=UTF-8");
                                                                response.getWriter().write(
                                                                                "{\"error\":\"invalid_oauth2_request\",\"message\":\"OAuth2 인증 요청이 유효하지 않습니다.\"}");
                                                                return;
                                                        }

                                                        String errorMessage = sanitizeOAuth2FailureCode(exception);
                                                        response.sendRedirect("/login?error="
                                                                        + URLEncoder.encode(errorMessage,
                                                                                        StandardCharsets.UTF_8));
                                                })
                                                .authorizationEndpoint(authorization -> authorization
                                                                .authorizationRequestRepository(
                                                                                cookieauthorizationrequestRepository)));

                // ========================================
                // 경로별 인가 설정 (그룹화된 상수 사용)
                // ========================================
                http
                                .authorizeHttpRequests((auth) -> auth
                                                .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR)
                                                .permitAll()
                                                // 1. OPTIONS 요청 허용 (CORS Preflight)
                                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                                                // 2. 인증 관련 공개 엔드포인트
                                                .requestMatchers(PUBLIC_AUTH_ENDPOINTS).permitAll()

                                                // 3. 시스템/테스트 엔드포인트
                                                .requestMatchers(publicSystemEndpoints()).permitAll()
                                                .requestMatchers("/actuator/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                                                // 4. 관리자 전용 엔드포인트
                                                .requestMatchers(ADMIN_ENDPOINTS).hasAnyRole("ADMIN", "SUPER_ADMIN")
                                                .requestMatchers(ADMIN_AI_ENDPOINTS).hasAnyRole("ADMIN",
                                                                "SUPER_ADMIN")

                                                // 5. 인증 필수 엔드포인트 (순서 중요: 구체적 경로 먼저)
                                                .requestMatchers(PRIVATE_AUTH_ENDPOINTS).authenticated()
                                                .requestMatchers(PRIVATE_AI_ENDPOINTS)
                                                .access((authentication, object) -> {
                                                        boolean authenticated = isAuthenticatedPrincipal(
                                                                        authentication.get(),
                                                                        object);
                                                        boolean internalTokenAuthorized = hasValidAiProxyInternalToken(
                                                                        object);
                                                        return new AuthorizationDecision(
                                                                        publicAiProxyInDev || authenticated
                                                                                        || internalTokenAuthorized);
                                                })
                                                .requestMatchers(HttpMethod.GET, "/api/parties/my").authenticated()
                                                .requestMatchers(HttpMethod.GET, "/api/cheer/posts/following")
                                                .authenticated()
                                                .requestMatchers(HttpMethod.GET, "/api/diary/games").permitAll()
                                                .requestMatchers("/api/diary/**").authenticated()
                                                .requestMatchers(HttpMethod.POST, "/api/predictions/vote")
                                                .authenticated()
                                                .requestMatchers(HttpMethod.GET, "/api/predictions/status/**")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/predictions/ranking/current-season")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/predictions/ranking/share/**")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/predictions/my-vote/**")
                                                .authenticated()
                                                .requestMatchers("/api/predictions/**").authenticated()

                                                // 6. 공개 GET 요청 엔드포인트
                                                .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                                                .requestMatchers(HttpMethod.GET, PUBLIC_PARTY_GET_ENDPOINTS).permitAll()

                                                // 7. 공개 API 엔드포인트 (구체적 인증 경로 먼저, 와일드카드 이전)
                                                .requestMatchers(HttpMethod.POST, "/api/stadiums/*/favorite")
                                                .authenticated()
                                                .requestMatchers(HttpMethod.DELETE, "/api/stadiums/*/favorite")
                                                .authenticated()
                                                .requestMatchers(HttpMethod.GET, "/api/stadiums/favorites")
                                                .authenticated()
                                                .requestMatchers(PUBLIC_API_ENDPOINTS).permitAll()

                                                // 8. 나머지 모든 요청은 인증 필요
                                                .anyRequest().authenticated())

                                // 302 리다이렉션 방지: 인증 실패 시 /login으로 리다이렉트 대신 401 응답 반환
                                .exceptionHandling((exceptionHandling) -> exceptionHandling
                                                .authenticationEntryPoint((request, response, authException) -> {
                                                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                                        response.setContentType("application/json;charset=UTF-8");

                                                        boolean invalidAuthor = Boolean.TRUE
                                                                        .equals(request.getAttribute("INVALID_AUTHOR"));
                                                        String errorCode = invalidAuthor ? "INVALID_AUTHOR"
                                                                        : "UNAUTHORIZED";
                                                        String message = invalidAuthor
                                                                        ? "인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요."
                                                                        : "인증이 필요합니다.";

                                                        String jsonResponse = String.format(
                                                                        "{\"success\":false,\"code\":\"%s\",\"message\":\"%s\",\"error\":\"Unauthorized\"}",
                                                                        errorCode,
                                                                        message.replace("\"", "\\\""));
                                                        response.getWriter().write(jsonResponse);
                                                }));

                // jwt 기반 인증처리니 세션을 stateless로 설정
                http
                                .sessionManagement((session) -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

                return http.build();
        }

        private String sanitizeOAuth2FailureCode(Exception exception) {
                String message = exception != null && exception.getMessage() != null
                                ? exception.getMessage().trim()
                                : "";
                if (message.isEmpty()) {
                        return "oauth2_auth_failed";
                }
                if (message.startsWith("KAKAO_")
                                || message.startsWith("NAVER_")
                                || message.startsWith("GOOGLE_")) {
                        return message;
                }
                if (message.equals(com.example.auth.service.OAuth2StateService.ERROR_CODE_STATE_STORE_UNAVAILABLE)) {
                        return message;
                }
                if ("oauth2_provider_payload_invalid".equals(message)) {
                        return message;
                }
                if ("manual_link_required".equals(message)) {
                        return message;
                }
                if (message.contains("계정 연동 세션이 만료")) {
                        return "oauth2_link_session_expired";
                }
                if (message.contains("계정 연동 실패")) {
                        return "oauth2_link_failed";
                }
                return "oauth2_auth_failed";
        }
}
