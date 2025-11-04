package com.example.demo.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod; 
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer; 
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler; 
import org.springframework.security.web.util.matcher.AntPathRequestMatcher; 

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.example.demo.Oauth2.CustomOAuth2UserService;
import com.example.demo.Oauth2.CustomSuccessHandler;
import com.example.demo.jwt.JWTFilter;
import com.example.demo.jwt.JWTUtil;
import com.example.demo.repo.RefreshRepository;
import com.example.demo.security.LoginFilter;
import com.example.demo.service.UserService; // UserService 임포트 유지

import jakarta.servlet.http.HttpServletResponse; 
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

import java.io.IOException;
import java.util.Arrays;
import jakarta.servlet.ServletException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
	
	private final AuthenticationConfiguration authenticationConfiguration;
	private final CustomOAuth2UserService customOAuth2UserService;
	private final CustomSuccessHandler customSuccessHandler;
    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;
    // 🚨 UserService 필드 제거 (순환 참조 방지)

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService,
    		CustomSuccessHandler customSuccessHandler, JWTUtil jwtUtil,
    		AuthenticationConfiguration authenticationConfiguration,
    		RefreshRepository refreshRepository
            /* 🚨 UserService 인자 제거 */) {
    	
    	this.authenticationConfiguration = authenticationConfiguration;
        this.customOAuth2UserService = customOAuth2UserService;
        this.customSuccessHandler = customSuccessHandler;
        this.jwtUtil = jwtUtil;
        this.refreshRepository = refreshRepository;
    }
    
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {

        return configuration.getAuthenticationManager();
    }
    
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {

        return new BCryptPasswordEncoder();
    }
    
    // [CORS Configuration Source Bean 정의]
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        // configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type")); 
        configuration.setAllowCredentials(true); 
        configuration.setMaxAge(3600L);

        // JWT Cookie를 설정한 경우 Set-Cookie 헤더를 노출하도록 설정 유지
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Set-Cookie")); 

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); 
        
        return source;
    }

    // 정적 자원 및 H2 콘솔 제외
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/h2-console/**")); 
    }
    
    // 💡 JWTFilter 빈 정의: 메서드 인자로 UserService를 주입받아 순환 참조 방지
    @Bean
    public JWTFilter jwtFilter(UserService userService) { // Spring이 UserService를 인자로 주입함
        return new JWTFilter(jwtUtil, userService); 
    }


    @Bean
    // 💡 [수정] JWTFilter를 인자로 받도록 변경하여 컴파일 오류 해결
    public SecurityFilterChain filterChain(HttpSecurity http, JWTFilter jwtFilter) throws Exception {

        // 1순위: CORS 활성화 및 CSRF 비활성화
        http
                .cors((cors) -> cors.configurationSource(corsConfigurationSource()));
        
        http
                .csrf((auth) -> auth.disable()); 
        
        //From 로그인 방식 disable
        http
        .formLogin((auth) -> auth.disable());

        //HTTP Basic 인증 방식 disable
        http
                .httpBasic((auth) -> auth.disable());
        
        
        // 💡 [수정] 인자로 받은 jwtFilter를 사용
		http
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
		
        // LoginFilter 처리 경로 명시 및 등록
        LoginFilter loginFilter = new LoginFilter(authenticationManager(authenticationConfiguration), jwtUtil, refreshRepository);
        
        // 🚀 CRITICAL FIX: 인증 성공 시 200 OK 상태로 응답을 강제 종료하는 핸들러 추가
        loginFilter.setAuthenticationSuccessHandler(new AuthenticationSuccessHandler() {
            @Override
            public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
                // 1. 상태 코드를 명시적으로 200 OK로 설정합니다. (302 방지)
                response.setStatus(HttpServletResponse.SC_OK);
                
                // 2. 응답 본문에 간단한 메시지를 쓰고 flush하여 응답을 즉시 종료(Commit)시킵니다.
                response.getWriter().write("Login successful via REST.");
                response.getWriter().flush();
                
                System.out.println("✅ LoginFilter Success Handler: Default redirect prevented and response committed with 200 OK.");
            }
        });
        
        // LoginFilter 등록: 기본 필터를 대체하여 인증 처리
        http
            .addFilterAt(loginFilter, UsernamePasswordAuthenticationFilter.class);

        // OAuth2 설정 
		http
            .oauth2Login((oauth2) -> oauth2
                .userInfoEndpoint((userInfoEndpointConfig) -> userInfoEndpointConfig
                    .userService(customOAuth2UserService))
                .successHandler(customSuccessHandler)
                .failureHandler((request, response, exception) -> {
                    System.err.println("🚨 OAuth2 로그인 최종 실패. 예외 메시지: " + exception.getMessage());
                    response.sendRedirect("/login?error=" + exception.getMessage()); 
                })
            );

        // 4. 경로별 인가 작업 - 권한 설정
        http
            .authorizeHttpRequests((auth) -> auth

                // 로그인 경로 /api/auth/login 은 필터가 처리해야 하므로 permitAll()에서 제외 유지
            	.requestMatchers("/api/auth/signup", "/api/auth/reissue").permitAll()
            	.requestMatchers("/", "/oauth2/**", "/login", "/error").permitAll()

            	.requestMatchers(HttpMethod.GET, "/api/cheer/posts", "/api/cheer/posts/**").permitAll() // 게시글 조회만 공개

                .requestMatchers("/api/stadiums/**").permitAll()
                .requestMatchers("/api/places/**").permitAll()
                .requestMatchers("/api/teams/**").permitAll()
                .requestMatchers("/api/games/**").permitAll()
                .requestMatchers("/api/parties/**").permitAll()
                .requestMatchers("/api/applications/**").permitAll()
                .requestMatchers("/api/chat/**").permitAll()
                .requestMatchers("/api/checkin/**").permitAll()

                // 2순위: OPTIONS 요청 허용 (Preflight 요청이 통과하도록)
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // 기존 권한 설정
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/team/be/**").hasRole("BE")
                
                
                
                // 나머지 모든 요청은 인증 필요
                .anyRequest().authenticated())
                
                // 302 리다이렉션 방지: 인증 실패 시 /login으로 리다이렉트 대신 401 응답 반환
                .exceptionHandling((exceptionHandling) ->
                    exceptionHandling.authenticationEntryPoint((request, response, authException) -> {
                        // 인증되지 않은 요청에 대해 302 대신 401 응답 강제
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write("Unauthorized: Authentication failed and no 'permitAll()' rule matched.");
                    })
                );
        		

        //세션 설정 : STATELESS (JWT 기반 인증이므로 세션을 사용하지 않음)
        http
            .sessionManagement((session) -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        


        return http.build();
    }
}

