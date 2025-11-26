package com.example.demo.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod; 
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.example.demo.Oauth2.CustomOAuth2UserService;
import com.example.demo.Oauth2.CustomSuccessHandler;
import com.example.demo.Oauth2.CookieAuthorizationRequestRepository; 
import com.example.demo.jwt.JWTFilter;
import com.example.demo.jwt.JWTUtil;
import com.example.demo.repo.RefreshRepository;

import jakarta.servlet.http.HttpServletResponse; 

import java.util.Arrays;


@Configuration
@EnableWebSecurity
public class SecurityConfig {
	
	private final AuthenticationConfiguration authenticationConfiguration;
	private final CustomOAuth2UserService customOAuth2UserService;
	private final CustomSuccessHandler customSuccessHandler;
    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;
    private final CookieAuthorizationRequestRepository cookieauthorizationrequestRepository;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService,
    		CustomSuccessHandler customSuccessHandler, JWTUtil jwtUtil,
    		AuthenticationConfiguration authenticationConfiguration,
    		RefreshRepository refreshRepository,
    		CookieAuthorizationRequestRepository cookieauthorizationrequestRepository
    		) {
    	
    	this.authenticationConfiguration = authenticationConfiguration;
        this.customOAuth2UserService = customOAuth2UserService;
        this.customSuccessHandler = customSuccessHandler;
        this.jwtUtil = jwtUtil;
        this.refreshRepository = refreshRepository;
        this.cookieauthorizationrequestRepository = cookieauthorizationrequestRepository;
    }
    
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
    
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    // CORS Configuration Source Bean 정의
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
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
    public JWTFilter jwtFilter() {
        return new JWTFilter(jwtUtil); 
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
                    .authorizationRequestRepository(cookieauthorizationrequestRepository)
                )
            );

        // 경로별 인가 작업 - 권한 설정
        http
            .authorizeHttpRequests((auth) -> auth
                .requestMatchers("/api/auth/login").permitAll()
            	.requestMatchers("/api/auth/signup", "/api/auth/reissue").permitAll()
            	.requestMatchers("/", "/oauth2/**", "/login", "/error").permitAll()
            	.requestMatchers("/api/profile/image").permitAll()
            	.requestMatchers(HttpMethod.GET, "/api/cheer/posts", "/api/cheer/posts/**").permitAll() // 게시글 조회만 공개
            	.requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
            	.requestMatchers("/api/auth/password-reset/request").permitAll()  // 요청
                .requestMatchers("/api/auth/password-reset/confirm").permitAll()  // 확인
                .requestMatchers("/api/stadiums/**").permitAll()
                .requestMatchers("/api/places/**").permitAll()
                .requestMatchers("/api/teams/**").permitAll()
                .requestMatchers("/api/games/**").permitAll()
                .requestMatchers("/api/parties/**").permitAll()
                .requestMatchers("/api/applications/**").permitAll()
                .requestMatchers("/api/chat/**").permitAll()
                .requestMatchers("/api/checkin/**").permitAll()

                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/api/chat/**").permitAll()
                .requestMatchers("/api/users/email-to-id").permitAll()
                // 2순위: OPTIONS 요청 허용 (Preflight 요청이 통과하도록)
                .requestMatchers("/api/diary/**").permitAll()
                .requestMatchers("/api/predictions/**").permitAll()  
                //OPTIONS 요청 허용 (Preflight 요청이 통과하도록)
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/kbo/league-start-dates").permitAll()
                .requestMatchers("/api/kbo/schedule/**").permitAll()
                .requestMatchers("/api/kbo/rankings/**").permitAll()
                .requestMatchers("/api/matches/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/predictions/vote").authenticated() // 인증된 사용자만 허용

                // 기존 권한 설정
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // 팀게시글 주소별 권한
                .requestMatchers("/team/be/**").hasRole("BE")
 
                // 나머지 모든 요청은 인증 필요
                .anyRequest().authenticated())
                
                // 302 리다이렉션 방지: 인증 실패 시 /login으로 리다이렉트 대신 401 응답 반환
	            .exceptionHandling((exceptionHandling) ->
	            exceptionHandling.authenticationEntryPoint((request, response, authException) -> {
	                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
	                response.setContentType("application/json;charset=UTF-8");
	                
	                String jsonResponse = "{\"success\":false,\"message\":\"인증이 필요합니다.\",\"error\":\"Unauthorized\"}";
	                response.getWriter().write(jsonResponse);
	            })
	        );
        		

        //jwt 기반 인증처리니 세션을 stateless로 설정
        http
            .sessionManagement((session) -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        
        return http.build();
    }
}