package com.example.demo.Oauth2;

import com.example.demo.dto.CustomOAuth2User;
import com.example.demo.dto.OAuth2Response;
import com.example.demo.dto.GoogleResponse; 
import com.example.demo.dto.KaKaoResponse; // 🚨 KakaoResponse DTO 임포트
import com.example.demo.entity.UserEntity;
import com.example.demo.repo.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Spring Security OAuth2의 User Service를 오버라이드하여 
 * 소셜 로그인 성공 후 사용자 정보를 DB에 저장하거나 업데이트하는 역할을 수행합니다.
 */
@Service
@Transactional
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 부모의 로직을 통해 사용자 정보(Attributes)를 가져옵니다.
        OAuth2User oAuth2User = super.loadUser(userRequest);
        
        // 1. OAuth2 제공자 식별
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        
        // 2. 제공자별 사용자 정보 객체 생성 (GoogleResponse/KakaoResponse가 OAuth2Response를 구현)
        OAuth2Response oAuth2Response;
        
        if (registrationId.equals("google")) {
            // Google 응답 처리 DTO
            oAuth2Response = new GoogleResponse(oAuth2User.getAttributes());
        } else if (registrationId.equals("kakao")) { 
            // Kakao 응답 처리 DTO
            oAuth2Response = new KaKaoResponse(oAuth2User.getAttributes());
        } else {
            // 지원하지 않는 제공자 처리 (Naver는 제외)
            throw new OAuth2AuthenticationException("Unsupported OAuth2 provider: " + registrationId);
        }

        // OAuth2Response에서 이메일을 추출하여 DB 조회에 사용
        String email = oAuth2Response.getEmail(); 

        if (email == null || email.isEmpty()) {
             // 이메일은 필수 정보이므로, 없을 경우 로그인 실패 처리
             throw new OAuth2AuthenticationException("Email is required for sign-up/login. (Provider: " + registrationId + ")");
        }


        // 3. DB에서 기존 사용자 찾기
        Optional<UserEntity> existData = userRepository.findByEmail(email);

        UserEntity userEntity = null;
        
        if (existData.isEmpty()) {
            // 4-1. 신규 사용자: 사용자 저장 (ROLE_USER 초기값 설정)
            userEntity = saveNewUser(oAuth2Response, registrationId);
        } else {
            // 4-2. 기존 사용자: 기존 데이터를 유지하며 OAuth2 관련 정보만 업데이트
            userEntity = updateExistingUser(existData.get(), oAuth2Response);
        }
        
        // 5. CustomOAuth2User 객체 반환 (인증 완료)
        // CustomOAuth2User DTO 생성 시 UserEntity의 DTO와 원본 Attributes를 사용합니다.
        return new CustomOAuth2User(userEntity.toDto(), oAuth2User.getAttributes());
    }

    /**
     * 신규 사용자를 DB에 저장하고 기본 역할(ROLE)을 부여합니다.
     */
    private UserEntity saveNewUser(OAuth2Response oAuth2Response, String provider) {
        // DTO에서 가져온 이름이 null이거나 비어있을 경우를 대비합니다.
        String userName = oAuth2Response.getName(); 
        
        UserEntity userEntity = UserEntity.builder()
                .email(oAuth2Response.getEmail())
                .name(userName != null && !userName.isEmpty() ? userName : "소셜 사용자") // null/empty 방지 처리
                .username(oAuth2Response.getEmail()) // username을 email로 통일 (로그인 식별자)
                .password("oauth2_user") // OAuth2 사용자는 패스워드가 필요 없으므로 임시 값 설정
                .role("ROLE_USER") // 🚨 신규 가입 시 기본 역할(ROLE_USER) 부여
                .provider(provider)
                .providerId(oAuth2Response.getProviderId())
                .favoriteTeam(null) 
                .build();

        return userRepository.save(userEntity);
    }

    /**
     * 기존 사용자의 정보를 업데이트합니다. 
     * 🚨 ROLE, favoriteTeam 등은 덮어쓰지 않고 보존합니다.
     */
    private UserEntity updateExistingUser(UserEntity existingUser, OAuth2Response oAuth2Response) {
        // OAuth2 관련 필드만 업데이트: 이름(Name)과 Provider ID만 갱신
        String userName = oAuth2Response.getName();
        
        // 이름이 DTO에서 제공되지 않으면 기존 이름 유지
        existingUser.setName(userName != null && !userName.isEmpty() ? userName : existingUser.getName()); 
        existingUser.setProviderId(oAuth2Response.getProviderId());
        
        // **중요:** 기존 ROLE이나 FavoriteTeam 값은 그대로 유지됩니다.

        return userRepository.save(existingUser);
    }
}