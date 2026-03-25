package com.example.auth.oauth2;

import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserProvider;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.AuthSecurityMonitoringService;
import com.example.common.ratelimit.RateLimitService;
import com.example.bega.auth.dto.OAuth2LinkStateData;
import com.example.bega.auth.service.OAuth2LinkStateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
                "spring.profiles.active=test",
                "spring.jwt.secret=test-jwt-secret-32-characters-long",
                "spring.jwt.refresh-expiration=86400000",
                "spring.datasource.url=jdbc:h2:mem:bega_oauth2_link_integration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false",
                "spring.jpa.open-in-view=false",
                "storage.type=oci",
                "oci.s3.endpoint=http://localhost:4566",
                "oci.s3.access-key=test-access-key",
                "oci.s3.secret-key=test-secret-key",
                "oci.s3.bucket=test-bucket",
                "oci.s3.region=ap-seoul-1"
})
@Transactional
class AccountLinkingIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private UserProviderRepository userProviderRepository;

        @Autowired
        private OAuth2LinkStateService oAuth2LinkStateService;

        @Autowired
        private CookieAuthorizationRequestRepository cookieAuthorizationRequestRepository;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private PlatformTransactionManager transactionManager;

        @MockitoBean
        private StringRedisTemplate redisTemplate;

        @MockitoBean
        private RateLimitService rateLimitService;

        private ValueOperations<String, String> valueOperations;
        private Map<String, String> redisStorage;

        @BeforeEach
        void setUp() {
                valueOperations = mock(ValueOperations.class);
                redisStorage = new ConcurrentHashMap<>();

                lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
                lenient().when(rateLimitService.isAllowed(anyString(), anyInt(), anyInt(), anyBoolean()))
                                .thenReturn(true);

                lenient().doAnswer(invocation -> {
                        String key = invocation.getArgument(0);
                        String value = invocation.getArgument(1);
                        redisStorage.put(key, value);
                        return null;
                }).when(valueOperations).set(anyString(), anyString(), anyLong(), any());

                lenient().when(valueOperations.getAndDelete(anyString()))
                                .thenAnswer(invocation -> {
                                        String key = invocation.getArgument(0);
                                        return redisStorage.remove(key);
                                });
        }

        @Test
        @DisplayName("OAuth2 계정 연동 전체 흐름: one-time linkToken 발급 -> state 저장/소비 -> UserProvider 생성")
        void accountLinkingFlow_saveLinkStateAndProcessLink() throws Exception {
                UserEntity user = localUser("link");

                String mvcResponse = mockMvc.perform(
                                get("/api/auth/link-token")
                                                .with(authentication(new UsernamePasswordAuthenticationToken(
                                                                user.getId(), null, List.of()))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.linkToken").isNotEmpty())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                JsonNode body = objectMapper.readTree(mvcResponse);
                String linkToken = body.get("linkToken").asText();

                String state = "state-" + UUID.randomUUID();
                MockHttpServletRequest saveReq = new MockHttpServletRequest();
                saveReq.setParameter("mode", "link");
                saveReq.setParameter("linkToken", linkToken);

                OAuth2AuthorizationRequest authorizationRequest = createAuthorizationRequest(state);
                MockHttpServletResponse saveRes = new MockHttpServletResponse();

                cookieAuthorizationRequestRepository.saveAuthorizationRequest(authorizationRequest, saveReq, saveRes);

                String oauth2AuthCookie = extractCookieValue(saveRes,
                                CookieAuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);

                assertThat(redisStorage.keySet()).anyMatch(key -> key.equals("oauth2:link:state:" + state));

                OAuth2LinkStateData savedData = objectMapper.readValue(redisStorage.get("oauth2:link:state:" + state),
                                OAuth2LinkStateData.class);
                assertThat(savedData.userId()).isEqualTo(user.getId());
                assertThat(savedData.failureReason()).isNull();

                MockHttpServletRequest callbackRequest = new MockHttpServletRequest();
                callbackRequest.setMethod("GET");
                callbackRequest.setParameter("state", state);
                callbackRequest.setCookies(new Cookie(
                                CookieAuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME,
                                oauth2AuthCookie));

                CustomOAuth2UserService service = new CustomOAuth2UserService(
                                userRepository,
                                userProviderRepository,
                                callbackRequest,
                                oAuth2LinkStateService,
                                cookieAuthorizationRequestRepository,
                                mock(AuthSecurityMonitoringService.class));

                OAuth2LinkStateData linkData = ReflectionTestUtils.invokeMethod(service, "extractLinkDataFromState");

                assertThat(linkData).isNotNull();
                assertThat(linkData.userId()).isEqualTo(user.getId());
                assertThat(linkData.failureReason()).isNull();

                UserEntity linkedUser = invokeProcessAccountLink(
                                service,
                                linkData,
                                "google",
                                "google-provider-id",
                                "social@example.com");

                assertThat(linkedUser).isNotNull();
                assertThat(linkedUser.getId()).isEqualTo(user.getId());

                UserProvider linkedProvider = userProviderRepository.findByUserIdAndProvider(user.getId(), "google")
                                .orElseThrow(() -> new IllegalStateException("연동 계정이 생성되지 않았습니다."));
                assertThat(linkedProvider.getProviderId()).isEqualTo("google-provider-id");
                assertThat(linkedProvider.getEmail()).isEqualTo("social@example.com");

                OAuth2LinkStateData consumedAgain = oAuth2LinkStateService.consumeLinkByState(state);
                assertThat(consumedAgain).isNull();
        }

        @Test
        @DisplayName("동일 providerId에 대한 동시 연동 시도는 정확히 한 건만 성공한다")
        void processAccountLink_allowsOnlyOneWinnerUnderConcurrency() throws Exception {
                UserEntity firstUser = localUser("first");
                UserEntity secondUser = localUser("second");

                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                ExecutorService executor = Executors.newFixedThreadPool(2);

                Future<String> firstResult = executor.submit(() -> invokeConcurrentLink(firstUser, ready, start));
                Future<String> secondResult = executor.submit(() -> invokeConcurrentLink(secondUser, ready, start));

                ready.await();
                start.countDown();

                String resultA = firstResult.get();
                String resultB = secondResult.get();
                executor.shutdownNow();

                assertThat(List.of(resultA, resultB)).anyMatch(result -> result.startsWith("success:"));
                assertThat(List.of(resultA, resultB)).anyMatch(result -> result.equals("oauth2_link_conflict"));

                UserProvider provider = userProviderRepository.findByProviderAndProviderId("google", "shared-provider-id")
                                .orElseThrow(() -> new IllegalStateException("연동 결과가 저장되지 않았습니다."));
                assertThat(List.of(firstUser.getId(), secondUser.getId())).contains(provider.getUser().getId());
        }

        @Test
        @DisplayName("동일 이메일의 기존 계정은 모든 provider에서 manual link를 요구한다")
        void processNormalLogin_requiresManualLinkForExistingEmailAcrossProviders() {
                UserEntity existing = localUser("manual");

                for (String provider : List.of("google", "kakao", "naver")) {
                        CustomOAuth2UserService service = new CustomOAuth2UserService(
                                        userRepository,
                                        userProviderRepository,
                                        new MockHttpServletRequest(),
                                        oAuth2LinkStateService,
                                        cookieAuthorizationRequestRepository,
                                        mock(AuthSecurityMonitoringService.class));

                        assertThat(invokeProcessNormalLoginCode(
                                        service,
                                        Optional.empty(),
                                        existing.getEmail(),
                                        "기존유저",
                                        provider,
                                        provider + "-provider-id",
                                        null))
                                        .isEqualTo("manual_link_required");
                }

                assertThat(userProviderRepository.findByUserId(existing.getId())).isEmpty();
        }

        private String invokeConcurrentLink(UserEntity user, CountDownLatch ready, CountDownLatch start) throws Exception {
                ready.countDown();
                start.await();

                TransactionTemplate tx = new TransactionTemplate(transactionManager);
                tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

                try {
                        return tx.execute(status -> {
                                try {
                                        CustomOAuth2UserService service = new CustomOAuth2UserService(
                                                        userRepository,
                                                        userProviderRepository,
                                                        new MockHttpServletRequest(),
                                                        oAuth2LinkStateService,
                                                        cookieAuthorizationRequestRepository,
                                                        mock(AuthSecurityMonitoringService.class));
                                        invokeProcessAccountLink(
                                                        service,
                                                        new OAuth2LinkStateData(user.getId(), System.currentTimeMillis(), null),
                                                        "google",
                                                        "shared-provider-id",
                                                        user.getEmail());
                                        return "success:" + user.getId();
                                } catch (Exception e) {
                                        throw new ConcurrentLinkAttemptException(e);
                                }
                        });
                } catch (ConcurrentLinkAttemptException e) {
                        if (e.getCause() instanceof OAuth2AuthenticationException authException) {
                                return authException.getError().getErrorCode();
                        }
                        throw e;
                }
        }

        private UserEntity localUser(String prefix) {
                TransactionTemplate tx = new TransactionTemplate(transactionManager);
                tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                return tx.execute(status -> userRepository.saveAndFlush(UserEntity.builder()
                                .uniqueId(UUID.randomUUID())
                                .email(prefix + "-" + UUID.randomUUID().toString().replace("-", "") + "@example.com")
                                .name(prefix + "-user")
                                .handle("u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                                .password("pwd-hash")
                                .role("ROLE_USER")
                                .provider("LOCAL")
                                .build()));
        }

        private OAuth2AuthorizationRequest createAuthorizationRequest(String state) {
                return OAuth2AuthorizationRequest.authorizationCode()
                                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                                .clientId("client-id")
                                .redirectUri("http://localhost:8080/login/oauth2/code/google")
                                .scopes(Set.of("email", "profile"))
                                .state(state)
                                .authorizationRequestUri(
                                                "https://accounts.google.com/o/oauth2/v2/auth?client_id=client-id&state="
                                                                + state)
                                .build();
        }

        private String extractCookieValue(MockHttpServletResponse response, String cookieName) {
                return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                                .filter(header -> header.startsWith(cookieName + "="))
                                .findFirst()
                                .map(header -> {
                                        int start = cookieName.length() + 1;
                                        int end = header.indexOf(';');
                                        return end > start ? header.substring(start, end) : header.substring(start);
                                })
                                .orElseThrow(() -> new IllegalStateException("쿠키가 존재하지 않습니다: " + cookieName));
        }

        private UserEntity invokeProcessAccountLink(
                        CustomOAuth2UserService service,
                        OAuth2LinkStateData linkData,
                        String provider,
                        String providerId,
                        String email) throws Exception {
                Method method = CustomOAuth2UserService.class.getDeclaredMethod(
                                "processAccountLink",
                                OAuth2LinkStateData.class,
                                String.class,
                                String.class,
                                String.class);
                method.setAccessible(true);

                try {
                        return (UserEntity) method.invoke(service, linkData, provider, providerId, email);
                } catch (InvocationTargetException e) {
                        if (e.getCause() instanceof Exception exception) {
                                throw exception;
                        }
                        throw e;
                }
        }

        private String invokeProcessNormalLoginCode(
                        CustomOAuth2UserService service,
                        Optional<UserProvider> userProviderOpt,
                        String email,
                        String userName,
                        String provider,
                        String providerId,
                        String profileImageUrl) {
                try {
                        Method method = CustomOAuth2UserService.class.getDeclaredMethod(
                                        "processNormalLogin",
                                        Optional.class,
                                        String.class,
                                        String.class,
                                        String.class,
                                        String.class,
                                        String.class);
                        method.setAccessible(true);
                        method.invoke(service, userProviderOpt, email, userName, provider, providerId, profileImageUrl);
                        return "success";
                } catch (InvocationTargetException e) {
                        if (e.getCause() instanceof OAuth2AuthenticationException authException) {
                                return authException.getError().getErrorCode();
                        }
                        throw new RuntimeException(e.getCause());
                } catch (ReflectiveOperationException e) {
                        throw new RuntimeException(e);
                }
        }

        private static final class ConcurrentLinkAttemptException extends RuntimeException {
                private ConcurrentLinkAttemptException(Exception cause) {
                        super(cause);
                }
        }
}
