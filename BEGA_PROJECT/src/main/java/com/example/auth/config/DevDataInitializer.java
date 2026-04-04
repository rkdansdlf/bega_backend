package com.example.auth.config;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.auth.entity.UserProvider;
import com.example.auth.repository.UserProviderRepository;
import com.example.mate.entity.SellerPayoutProfile;
import com.example.mate.repository.SellerPayoutProfileRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.Locale;

@Slf4j
@Component
@Profile({ "dev", "local" })
@ConditionalOnProperty(prefix = "app.dev-data", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DevDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserProviderRepository userProviderRepository;
    private final SellerPayoutProfileRepository sellerPayoutProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${TEST_EMAIL:test@example.com}")
    private String testEmail;

    @Value("${TEST_PASSWORD:testpassword}")
    private String testPassword;

    @Value("${TEST_HOST_EMAIL:host@example.com}")
    private String testHostEmail;

    @Value("${TEST_HOST_PASSWORD:hostpassword}")
    private String testHostPassword;

    @Value("${TEST_BUYER_EMAIL:buyer@example.com}")
    private String testBuyerEmail;

    @Value("${TEST_BUYER_PASSWORD:buyerpassword}")
    private String testBuyerPassword;

    @Value("${TEST_ADMIN_EMAIL:}")
    private String testAdminEmail;

    @Value("${TEST_ADMIN_PASSWORD:}")
    private String testAdminPassword;

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting DevDataInitializer to verify test accounts...");

        // 1. Smoke test 계정
        createTestUserIfNotFound(testEmail, testPassword, "test", "Smoke Tester", false, "ROLE_USER");

        // 2. 파티 생성자 계정
        createTestUserIfNotFound(testHostEmail, testHostPassword, "host", "Party Host", true, "ROLE_USER");

        // 3. 파티 구매자 계정
        createTestUserIfNotFound(testBuyerEmail, testBuyerPassword, "buyer", "Party Buyer", false, "ROLE_USER");

        // 4. Dev 관리자 계정
        createTestUserIfNotFound(testAdminEmail, testAdminPassword, "devadmin", "Dev Admin", false, "ROLE_ADMIN");

        log.info("DevDataInitializer finished applying test accounts.");
    }

    private void createTestUserIfNotFound(String email, String password, String handlePrefix, String name,
            boolean provisionPayoutProfile,
            String role) {
        if (isBlank(email) || isBlank(password)) {
            log.warn("Skipping test user initialization for {} because TEST_* credentials are blank.", name);
            return;
        }

        if (!userRepository.existsByEmail(email)) {
            // handle uniqueness check (simple append for dev)
            String normalizedHandlePrefix = handlePrefix == null ? "" : handlePrefix.trim().toLowerCase(Locale.ROOT);
            String finalHandle = normalizedHandlePrefix;
            int counter = 1;
            while (userRepository.existsByHandle(finalHandle)) {
                finalHandle = normalizedHandlePrefix + counter;
                counter++;
            }

            UserEntity user = UserEntity.builder()
                    .uniqueId(UUID.randomUUID())
                    .handle(finalHandle)
                    .name(name)
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .role(role)
                    .enabled(true)
                    .locked(false)
                    .cheerPoints(100000) // 넉넉한 초기 포인트 지급 (테스트용)
                    .build();

            UserEntity savedUser = userRepository.save(user);
            linkSocialProvider(savedUser);
            if (provisionPayoutProfile) {
                linkSellerPayoutProfile(savedUser);
            }
            log.info("Created test user: {} (email: {}, handle: {})", name, email, finalHandle);
        } else {
            log.info("Test user already exists: {} (email: {})", name, email);
            Optional<UserEntity> existingUser = userRepository.findWithProvidersByEmail(email);
            existingUser.ifPresent(u -> {
                boolean updated = false;
                if (u.getPassword() == null || !passwordEncoder.matches(password, u.getPassword())) {
                    u.setPassword(passwordEncoder.encode(password));
                    updated = true;
                }
                if (!role.equals(u.getRole())) {
                    u.setRole(role);
                    updated = true;
                }
                if (!u.isEnabled()) {
                    u.setEnabled(true);
                    updated = true;
                }
                if (u.isLocked()) {
                    u.setLocked(false);
                    updated = true;
                }
                if (u.getCheerPoints() == null || u.getCheerPoints() < 100000) {
                    u.setCheerPoints(100000);
                    updated = true;
                }
                if (updated) {
                    userRepository.save(u);
                    log.info("Synchronized test user baseline for {} (email: {})", name, email);
                }
                linkSocialProvider(u);
                if (provisionPayoutProfile) {
                    linkSellerPayoutProfile(u);
                }
            });
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void linkSellerPayoutProfile(UserEntity user) {
        boolean hasTossProfile = sellerPayoutProfileRepository.findByUserIdAndProvider(user.getId(), "TOSS")
                .isPresent();
        if (!hasTossProfile) {
            SellerPayoutProfile profile = SellerPayoutProfile.builder()
                    .userId(user.getId())
                    .provider("TOSS")
                    .providerSellerId("mock-seller-id-" + user.getId())
                    .kycStatus("COMPLETED")
                    .metadataJson("{\"test\":true}")
                    .build();
            sellerPayoutProfileRepository.save(profile);
            log.info("Linked mock Toss SellerPayoutProfile for user: {}", user.getEmail());
        }
    }

    private void linkSocialProvider(UserEntity user) {
        boolean hasKakao = user.getProviders().stream()
                .anyMatch(p -> "kakao".equals(p.getProvider()));

        if (!hasKakao) {
            UserProvider provider = UserProvider.builder()
                    .user(user)
                    .provider("kakao")
                    .providerId("mock-kakao-id-" + user.getId())
                    .email(user.getEmail())
                    .build();
            userProviderRepository.save(provider);
            user.getProviders().add(provider);
            log.info("Linked mock kakao provider for user: {}", user.getEmail());
        }
    }
}
