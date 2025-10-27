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
// 🚨 새로 추가된 Import
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

import jakarta.servlet.http.HttpServletResponse; 

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
	
	private final AuthenticationConfiguration authenticationConfiguration;
	private final CustomOAuth2UserService customOAuth2UserService;
	private final CustomSuccessHandler customSuccessHandler;
    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService,
    		CustomSuccessHandler customSuccessHandler, JWTUtil jwtUtil,
    		AuthenticationConfiguration authenticationConfiguration,
    		RefreshRepository refreshRepository) {
    	
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
        
        // 프론트엔드 주소 명시
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000"));
        
        // 허용할 메서드 정의 (Preflight 요청을 위한 OPTIONS 포함)
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        
        // Authorization, Content-Type 헤더 허용
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type"));
        
        // 중요: 쿠키 기반 인증 정보 전송 허용
        configuration.setAllowCredentials(true); 
        
        // Preflight 요청 캐싱 시간 설정 (3600초 = 1시간)
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 모든 경로("/**")에 CORS 설정을 적용
        source.registerCorsConfiguration("/**", configuration); 
        
        return source;
    }

    // ===================================================================
    // 🚨 최종 해결책: WebSecurityCustomizer를 사용하여 특정 경로를 필터 체인에서 완전히 제외
    // 두 가지 패턴을 모두 사용하여 확실하게 제외합니다.
    // ===================================================================
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring()
                // /api/test/hello, /api/test/echo와 같은 하위 경로 제외
                .requestMatchers("/api/auth/**") 
                // /api/test 그 자체 경로도 제외 (혹시 모를 Trailing Slash 문제 해결)
                .requestMatchers("/api/auth"); 
    }


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // ===================================================================
        // 1순위: CORS 활성화 및 CSRF 비활성화
        // ===================================================================
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
        
        
        // 필터 추가 (JWTFilter는 인증 전에 토큰 검사, LoginFilter는 실제 로그인 처리)
		http
            .addFilterBefore(new JWTFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class);
		
        // LoginFilter 처리 경로 명시
        LoginFilter loginFilter = new LoginFilter(authenticationManager(authenticationConfiguration), jwtUtil, refreshRepository);
        
        // 🔑 핵심 수정: POST 요청만 인증 필터가 처리하도록 명시적으로 설정합니다.
        // GET 요청은 이제 이 필터를 건너뛰고 다음 permitAll() 설정으로 전달됩니다.
        loginFilter.setRequiresAuthenticationRequestMatcher(new AntPathRequestMatcher("/login", HttpMethod.POST.name()));
        
        loginFilter.setFilterProcessesUrl("/login"); // 로그인 처리 경로 설정 (POST /login)
        
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

        // 4. 경로별 인가 작업 - 권한 설정의 순서가 가장 중요합니다.
        http
            .authorizeHttpRequests((auth) -> auth
                // /api/test/** 경로는 WebSecurityCustomizer가 처리하므로, 여기서는 제거합니다.
            	.requestMatchers("/", "/oauth2/**", "/login", "/error", "/reissue", "/join").permitAll()
                
                // 2순위: OPTIONS 요청 허용 (Preflight 요청이 통과하도록)
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() 
                
                // 기존 권한 설정
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/team/be/**").hasRole("BE") 
                
                // 나머지 모든 요청은 인증 필
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
