package com.example.auth.oauth2;

import com.example.auth.dto.CustomOAuth2User;
import com.example.auth.dto.OAuth2Response;
import com.example.auth.dto.GoogleResponse;
import com.example.auth.dto.KaKaoResponse;
import com.example.auth.dto.NaverResponse;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
@lombok.extern.slf4j.Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final com.example.auth.repository.UserProviderRepository userProviderRepository;
    private final jakarta.servlet.http.HttpServletRequest request;
    private final com.example.auth.util.JWTUtil jwtUtil;

    public CustomOAuth2UserService(UserRepository userRepository,
            com.example.auth.repository.UserProviderRepository userProviderRepository,
            jakarta.servlet.http.HttpServletRequest request,
            com.example.auth.util.JWTUtil jwtUtil) {
        this.userRepository = userRepository;
        this.userProviderRepository = userProviderRepository;
        this.request = request;
        this.jwtUtil = jwtUtil;
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
        String email;
        try {
            email = oAuth2Response.getEmail();
        } catch (IllegalStateException e) {
            // Kakao 등 provider별 상세 에러 메시지 전달
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.startsWith("KAKAO_") || errorMsg.startsWith("NAVER_") || errorMsg.startsWith("GOOGLE_"))) {
                throw new OAuth2AuthenticationException(errorMsg);
            }
            throw new OAuth2AuthenticationException("소셜 로그인 중 이메일 정보를 가져올 수 없습니다: " + e.getMessage());
        }

        if (email == null || email.isEmpty()) {
            throw new OAuth2AuthenticationException("이메일 정보는 필수입니다 (Provider: " + registrationId + ")");
        }
        // 이메일 정규화 (소문자 변환 및 공백 제거)
        email = email.trim().toLowerCase();

        String provider = registrationId;
        String providerId = oAuth2Response.getProviderId();
        String userName = oAuth2Response.getName();

        // 5. UserProvider(연동 계정) 조회
        Optional<com.example.auth.entity.UserProvider> userProviderOpt = userProviderRepository
                .findByProviderAndProviderId(provider, providerId);

        UserEntity userEntity;

        // 쿠키에서 연동 모드 확인 (CookieAuthorizationRequestRepository에서 저장함)
        String linkMode = null;
        String linkUserIdStr = null;

        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if ("oauth2_link_mode".equals(cookie.getName())) {
                    linkMode = cookie.getValue();
                } else if ("oauth2_link_user_id".equals(cookie.getName())) {
                    linkUserIdStr = cookie.getValue();
                }
            }
        }

        log.info("=== CustomOAuth2UserService loadUser ===");
        log.info("RegistrationId: {}", registrationId);
        log.info("Email: {}", email);
        log.info("LinkMode Session: {}", linkMode);
        log.info("LinkUserId Session: {}", linkUserIdStr);

        // [Security Fix] Verify if linkUserIdStr is a valid signed token
        if ("link".equals(linkMode) && linkUserIdStr != null && !jwtUtil.isExpired(linkUserIdStr)) {
            // [계정 연동 모드]
            Long userId = jwtUtil.getUserId(linkUserIdStr); // Extract trusted ID from token

            if (userId == null) {
                log.error("Invalid Link Token. Aborting Link.");
                throw new OAuth2AuthenticationException("유효하지 않은 연동 요청입니다.");
            }

            log.info("Processing Account Link for UserID: {}", userId);

            Optional<UserEntity> targetUserOpt = userRepository.findById(userId);

            if (targetUserOpt.isPresent()) {
                userEntity = targetUserOpt.get();

                // [중복 연동 방지 및 소유권 이전 로직]
                // 1. 해당 provider + providerId로 이미 연동된 정보가 있는지 전역 조회
                Optional<com.example.auth.entity.UserProvider> existingProviderOpt = userProviderRepository
                        .findByProviderAndProviderId(provider, providerId);

                if (existingProviderOpt.isPresent()) {
                    com.example.auth.entity.UserProvider existingProvider = existingProviderOpt.get();
                    if (!existingProvider.getUser().getId().equals(userId)) {
                        // 다른 사용자에게 이미 연동되어 있음 -> 소유권 이전 (기존 버그로 생성된 껍데기 계정 등)
                        log.warn("Conflict Detected: Social Account already linked to User ID {}",
                                existingProvider.getUser().getId());
                        log.info("Moving Linkage to Target User ID {}", userId);

                        existingProvider.setUser(userEntity); // 소유자 변경
                        userProviderRepository.saveAndFlush(existingProvider); // 즉시 반영
                    } else {
                        // 이미 내 계정에 연동되어 있음 (정상)
                        log.info("Already linked to correct user.");
                    }
                } else {
                    // 2. 연동된 정보가 없으면 신규 연동 생성
                    linkAccount(userEntity, provider, providerId);
                }

                log.info("Account Linked/Moved Successfully for User: {}", userEntity.getEmail());

                // 사용 완료된 쿠키 제거는 CustomSuccessHandler에서 처리함
                // (여기서는 HttpServletResponse에 접근하기 어려움)
            } else {
                log.error("Target User Not Found for ID: {}", userId);
                throw new OAuth2AuthenticationException("연동할 대상 사용자를 찾을 수 없습니다.");
            }
        } else if (userProviderOpt.isPresent()) {
            // [일반 로그인] 5-1. 이미 연동된 계정이 있는 경우 -> 해당 사용자 반환 (기존 로직)
            log.info("Existing Provider Found. Logging in.");
            userEntity = userProviderOpt.get().getUser();
            // 이름 강제 업데이트 방지 (선택적 업데이트)
            updateUser(userEntity, userName);
        } else {
            // [일반 로그인] 5-2. 연동된 계정이 없는 경우 -> 이메일로 기존 사용자 검색 (기존 로직)
            log.info("No Provider Found. Checking by Email.");
            Optional<UserEntity> existingUserOpt = userRepository.findByEmail(email);

            if (existingUserOpt.isPresent()) {
                UserEntity existingUser = existingUserOpt.get();

                // [Security Fix] 비밀번호가 있는 계정(일반 회원가입)에 대한 자동 연동 차단
                // 비밀번호 계정에 소셜 계정을 자동 연동하면 계정 탈취 위험이 있음
                if (existingUser.getPassword() != null && !existingUser.getPassword().isEmpty()) {
                    log.warn("Auto-link blocked for password-protected account: {}", email);
                    throw new OAuth2AuthenticationException(
                        "ACCOUNT_EXISTS_WITH_PASSWORD:이 이메일은 이미 일반 회원가입으로 등록되어 있습니다. " +
                        "기존 계정으로 로그인 후 마이페이지에서 소셜 계정을 연동해주세요."
                    );
                }

                // OAuth2 계정끼리의 자동 연동은 허용 (기존 동작 유지)
                log.info("Existing OAuth2 Email Found. Auto-linking.");
                userEntity = existingUser;
                linkAccount(userEntity, provider, providerId);
                // 이름 강제 업데이트 방지
                updateUser(userEntity, userName);
            } else {
                // B. 신규 사용자 -> 회원가입 + 연동 정보 생성
                log.info("New User Required. Creating Account.");
                userEntity = saveNewUser(email, userName, provider, providerId);
            }
        }

        // [일일 출석 보너스 지급]
        java.time.LocalDate today = java.time.LocalDate.now();
        if (userEntity.getLastBonusDate() == null || !userEntity.getLastBonusDate().equals(today)) {
            userEntity.addCheerPoints(5);
            userEntity.setLastBonusDate(today);
            userRepository.save(userEntity);
            log.info("Daily Login Bonus (5 points) awarded to Social User: {}", userEntity.getEmail());
        }

        // 6. CustomOAuth2User 객체 반환
        return new CustomOAuth2User(userEntity.toDto(), oAuth2User.getAttributes());
    }

    private UserEntity saveNewUser(String email, String name, String provider, String providerId) {
        // UserEntity 생성
        UserEntity userEntity = UserEntity.builder()
                .email(email)
                .name(name != null && !name.isEmpty() ? name : "소셜 사용자")
                .password(null) // OAuth2 users don't have passwords
                .role("ROLE_USER")
                // 기존 provider 필드는 하위 호환성을 위해 유지하거나 비워둠 (여기서는 메인 provider로 설정)
                .provider(provider)
                .providerId(providerId)
                .favoriteTeam(null)
                .uniqueId(java.util.UUID.randomUUID()) // Initialize uniqueId
                .handle(generateRandomHandle()) // Initialize handle
                .build();

        UserEntity savedUser = userRepository.save(java.util.Objects.requireNonNull(userEntity));

        // UserProvider 생성
        linkAccount(java.util.Objects.requireNonNull(savedUser), provider, providerId);

        return savedUser;
    }

    private String generateRandomHandle() {
        // Handle max length 15.
        // Format: u_{random_12_chars}
        String randomHex = java.util.UUID.randomUUID().toString().replace("-", "");
        return "u_" + randomHex.substring(0, 12);
    }

    private void linkAccount(UserEntity user, String provider, String providerId) {
        // 이미 해당 프로바이더로 연동된 정보가 있는지 확인 (강한 제약 조건 대응: 1인 1계정/프로바이더)
        Optional<com.example.auth.entity.UserProvider> existingLink = userProviderRepository
                .findByUserIdAndProvider(user.getId(), provider);

        if (existingLink.isPresent()) {
            com.example.auth.entity.UserProvider up = existingLink.get();
            // Provider ID가 달라졌다면 업데이트 (계정 변경 등의 경우)
            if (!up.getProviderId().equals(providerId)) {
                up.setProviderId(providerId);
                userProviderRepository.save(up);
            }
            return;
        }

        com.example.auth.entity.UserProvider userProvider = com.example.auth.entity.UserProvider.builder()
                .user(user)
                .provider(provider)
                .providerId(providerId)
                .build();

        userProviderRepository.save(java.util.Objects.requireNonNull(userProvider));
    }

    private void updateUser(UserEntity user, String newName) {
        // 이름이 "소셜 사용자"이거나 비어있을 때만 소셜 정보로 업데이트 (기존 닉네임 유지)
        if (user.getName() == null || user.getName().isEmpty() || "소셜 사용자".equals(user.getName())) {
            if (newName != null && !newName.isEmpty()) {
                user.setName(newName);
                userRepository.save(user);
            }
        }
    }
}
