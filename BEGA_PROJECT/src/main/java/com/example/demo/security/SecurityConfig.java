package com.example.demo.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.demo.Oauth2.CustomOAuth2UserService;
import com.example.demo.Oauth2.CustomSuccessHandler;
import com.example.demo.jwt.JWTFilter;
import com.example.demo.jwt.JWTUtil;
import com.example.demo.repo.RefreshRepository;

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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        //csrf disable
        http
                .csrf((auth) -> auth.disable());

        //From 로그인 방식 disable
        http
                .formLogin((auth) -> auth.disable());

        //HTTP Basic 인증 방식 disable
        http
                .httpBasic((auth) -> auth.disable());
        
        
        
        //JWTFilter 추가
		http
        .addFilterBefore(new JWTFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class);
		
		 http
         .addFilterAt(new LoginFilter(authenticationManager(authenticationConfiguration), jwtUtil, refreshRepository), UsernamePasswordAuthenticationFilter.class);

        //oauth2
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
        //경로별 인가 작업
        http
                .authorizeHttpRequests((auth) -> auth
                        .requestMatchers("/", "/oauth2/**", "/login/**", "/error", "/reissue").permitAll()
                        .anyRequest().authenticated());
        		

        //세션 설정 : STATELESS
        http
                .sessionManagement((session) -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        


        return http.build();
    }
}