package com.example.demo.Oauth2;

import com.example.demo.dto.CustomOAuth2User;
import com.example.demo.dto.OAuth2Response;
import com.example.demo.dto.GoogleResponse;
import com.example.demo.dto.KaKaoResponse;
import com.example.demo.dto.NaverResponse;
import com.example.demo.entity.UserEntity;
import com.example.demo.repo.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final com.example.demo.repo.UserProviderRepository userProviderRepository;

    public CustomOAuth2UserService(UserRepository userRepository,
            com.example.demo.repo.UserProviderRepository userProviderRepository) {
        this.userRepository = userRepository;
        this.userProviderRepository = userProviderRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 1. 사용자 정보(Attributes) 가져오기
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // 2. OAuth2 provider 식별
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        // 3. provider별 사용자 정보 객체 생성
        OAuth2Response oAuth2Response;
        if (registrationId.equals("google")) {
            oAuth2Response = new GoogleResponse(oAuth2User.getAttributes());
        } else if (registrationId.equals("kakao")) {
            oAuth2Response = new KaKaoResponse(oAuth2User.getAttributes());
        } else if (registrationId.equals("naver")) {
            oAuth2Response = new NaverResponse(oAuth2User.getAttributes());
        } else {
            throw new OAuth2AuthenticationException("해당 소셜로그인은 지원하지 않습니다.: " + registrationId);
        }

        // 4. 이메일 추출 (필수)
        String email = oAuth2Response.getEmail();
        if (email == null || email.isEmpty()) {
            throw new OAuth2AuthenticationException("이메일 정보는 필수입니다 (Provider: " + registrationId + ")");
        }
        // 이메일 정규화 (소문자 변환 및 공백 제거)
        email = email.trim().toLowerCase();

        String provider = registrationId;
        String providerId = oAuth2Response.getProviderId();
        String userName = oAuth2Response.getName();

        // 5. UserProvider(연동 계정) 조회
        Optional<com.example.demo.entity.UserProvider> userProviderOpt = userProviderRepository
                .findByProviderAndProviderId(provider, providerId);

        UserEntity userEntity;

        if (userProviderOpt.isPresent()) {
            // 5-1. 이미 연동된 계정이 있는 경우 -> 해당 사용자 반환
            userEntity = userProviderOpt.get().getUser();
            // 필요 시 사용자 정보(이름 등) 업데이트
            updateUser(userEntity, userName);
        } else {
            // 5-2. 연동된 계정이 없는 경우 -> 이메일로 기존 사용자 검색
            Optional<UserEntity> existingUserOpt = userRepository.findByEmail(email);

            if (existingUserOpt.isPresent()) {
                // A. 기존 사용자가 존재함 -> 계정 자동 연동 (Auto-Link)
                userEntity = existingUserOpt.get();
                linkAccount(userEntity, provider, providerId);
                updateUser(userEntity, userName);
            } else {
                // B. 신규 사용자 -> 회원가입 + 연동 정보 생성
                userEntity = saveNewUser(email, userName, provider, providerId);
            }
        }

        // 6. CustomOAuth2User 객체 반환
        return new CustomOAuth2User(userEntity.toDto(), oAuth2User.getAttributes());
    }

    private UserEntity saveNewUser(String email, String name, String provider, String providerId) {
        // UserEntity 생성
        UserEntity userEntity = UserEntity.builder()
                .email(email)
                .name(name != null && !name.isEmpty() ? name : "소셜 사용자")
                .password(java.util.UUID.randomUUID().toString()) // 랜덤 비밀번호
                .role("ROLE_USER")
                // 기존 provider 필드는 하위 호환성을 위해 유지하거나 비워둠 (여기서는 메인 provider로 설정)
                .provider(provider)
                .providerId(providerId)
                .favoriteTeam(null)
                .build();

        UserEntity savedUser = userRepository.save(java.util.Objects.requireNonNull(userEntity));

        // UserProvider 생성
        linkAccount(java.util.Objects.requireNonNull(savedUser), provider, providerId);

        return savedUser;
    }

    private void linkAccount(UserEntity user, String provider, String providerId) {
        // 이미 해당 프로바이더로 연동된 정보가 있는지 확인 (강한 제약 조건 대응: 1인 1계정/프로바이더)
        Optional<com.example.demo.entity.UserProvider> existingLink = userProviderRepository
                .findByUserIdAndProvider(user.getId(), provider);

        if (existingLink.isPresent()) {
            com.example.demo.entity.UserProvider up = existingLink.get();
            // Provider ID가 달라졌다면 업데이트 (계정 변경 등의 경우)
            if (!up.getProviderId().equals(providerId)) {
                up.setProviderId(providerId);
                userProviderRepository.save(up);
            }
            return;
        }

        com.example.demo.entity.UserProvider userProvider = com.example.demo.entity.UserProvider.builder()
                .user(user)
                .provider(provider)
                .providerId(providerId)
                .build();

        userProviderRepository.save(java.util.Objects.requireNonNull(userProvider));
    }

    private void updateUser(UserEntity user, String newName) {
        if (newName != null && !newName.isEmpty() && !newName.equals(user.getName())) {
            user.setName(newName);
            userRepository.save(user);
        }
    }
}
