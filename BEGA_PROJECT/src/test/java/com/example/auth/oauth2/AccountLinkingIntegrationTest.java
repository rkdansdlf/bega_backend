package com.example.auth.oauth2;

import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserProvider;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=6379",
        "spring.data.redis.repositories.enabled=false",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1",
        "spring.autoconfigure.exclude=io.awspring.cloud.autoconfigure.s3.S3AutoConfiguration"
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

        @MockitoBean
        private StringRedisTemplate redisTemplate;

        private ValueOperations<String, String> valueOperations;
        private Map<String, String> redisStorage;

        @BeforeEach
        void setUp() {
                valueOperations = mock(ValueOperations.class);
                redisStorage = new ConcurrentHashMap<>();

                lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

                // Simulate minimal Redis set/getAndDelete operations
                // Simulate minimal Redis set/getAndDelete operations
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
        @DisplayName("OAuth2 계정 연동 전체 흐름: linkToken 발급 -> state 저장/소비 -> UserProvider 생성")
        void accountLinkingFlow_saveLinkStateAndProcessLink() throws Exception {
                UserEntity user = UserEntity.builder()
                                .uniqueId(UUID.randomUUID())
                                .email("link-" + UUID.randomUUID().toString().replace("-", "") + "@example.com")
                                .name("연동테스트유저")
                                .handle("user" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                                .password("pwd-hash")
                                .role("ROLE_USER")
                                .provider("LOCAL")
                                .build();
                user = userRepository.save(user);

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
                String redirectUri = "http://localhost:5173/mypage";
                MockHttpServletRequest saveReq = new MockHttpServletRequest();
                saveReq.setParameter("mode", "link");
                saveReq.setParameter("linkToken", linkToken);
                saveReq.setParameter("redirect_uri", redirectUri);

                OAuth2AuthorizationRequest authorizationRequest = createAuthorizationRequest(state);
                MockHttpServletResponse saveRes = new MockHttpServletResponse();

                cookieAuthorizationRequestRepository.saveAuthorizationRequest(authorizationRequest, saveReq, saveRes);

                String oauth2AuthCookie = extractCookieValue(saveRes,
                                CookieAuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);

                String redisKey = "oauth2:link:" + state;
                assertThat(redisStorage).containsKey(redisKey);
                OAuth2LinkStateData savedData = objectMapper.readValue(redisStorage.get(redisKey),
                                OAuth2LinkStateData.class);
                assertThat(savedData.userId()).isEqualTo(user.getId());
                assertThat(savedData.mode()).isEqualTo("link");
                assertThat(savedData.failureReason()).isNull();
                assertThat(savedData.redirectUri()).isEqualTo(redirectUri);

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
                                cookieAuthorizationRequestRepository);

                OAuth2LinkStateData linkData = ReflectionTestUtils.invokeMethod(service, "extractLinkDataFromState");

                assertThat(linkData).isNotNull();
                assertThat(linkData.userId()).isEqualTo(user.getId());
                assertThat(linkData.mode()).isEqualTo("link");
                assertThat(linkData.failureReason()).isNull();

                UserEntity linkedUser = ReflectionTestUtils.invokeMethod(
                                service,
                                "processAccountLink",
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
                assertThat(redisStorage).doesNotContainKey(redisKey);
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
}
