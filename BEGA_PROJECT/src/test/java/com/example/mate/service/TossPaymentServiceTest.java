package com.example.mate.service;

import com.example.mate.config.TossPaymentConfig;
import com.example.mate.dto.TossPaymentDTO;
import com.example.mate.exception.TossPaymentException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TossPaymentService 테스트")
class TossPaymentServiceTest {

    private static final String TEST_SECRET_KEY = "test_sk_abc123";
    private static final String CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";

    @Mock
    private TossPaymentConfig tossPaymentConfig;
    @Mock
    private MatePaymentModeService matePaymentModeService;

    private TossPaymentService tossPaymentService;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        // MockRestServiceServer를 RestTemplate 기반으로 생성, RestClient에 래핑
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        RestClient restClient = RestClient.create(restTemplate);
        ObjectMapper objectMapper = new ObjectMapper();

        tossPaymentService = new TossPaymentService(tossPaymentConfig, restClient, objectMapper, matePaymentModeService);
    }

    @Test
    @DisplayName("결제 승인 성공 - DONE 상태 반환")
    void confirmPayment_success() {
        // given
        stubTossEnabled();
        String paymentKey = "test_pk_abc123";
        String orderId = "MATE-1-2-1708500000000";
        int amount = 10000;

        String responseBody = """
                {
                    "paymentKey": "%s",
                    "orderId": "%s",
                    "status": "DONE",
                    "totalAmount": %d,
                    "method": "카드"
                }
                """.formatted(paymentKey, orderId, amount);

        mockServer.expect(requestTo(CONFIRM_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic dGVzdF9za19hYmMxMjM6")) // Base64("test_sk_abc123:")
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when
        TossPaymentDTO.ConfirmResponse response =
                tossPaymentService.confirmPayment(paymentKey, orderId, amount);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("DONE");
        assertThat(response.getTotalAmount()).isEqualTo(amount);
        assertThat(response.getMethod()).isEqualTo("카드");
        assertThat(response.getPaymentKey()).isEqualTo(paymentKey);
        assertThat(response.getOrderId()).isEqualTo(orderId);
        mockServer.verify();
    }

    @Test
    @DisplayName("결제 승인 실패 - Toss API 4xx 응답 시 TossPaymentException 발생")
    void confirmPayment_tossApiReturns4xx_throwsTossPaymentException() {
        // given
        stubTossEnabled();
        String errorBody = """
                {
                    "code": "NOT_FOUND_PAYMENT",
                    "message": "존재하지 않는 결제입니다."
                }
                """;

        mockServer.expect(requestTo(CONFIRM_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body(errorBody)
                        .contentType(MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() ->
                tossPaymentService.confirmPayment("invalid_key", "order_x", 10000))
                .isInstanceOf(TossPaymentException.class)
                .hasMessageContaining("결제 승인에 실패했습니다");
        mockServer.verify();
    }

    @Test
    @DisplayName("결제 승인 실패 - Toss API 401 응답 (잘못된 시크릿 키)")
    void confirmPayment_unauthorized_throwsTossPaymentException() {
        // given
        stubTossEnabled();
        String errorBody = """
                {
                    "code": "UNAUTHORIZED_KEY",
                    "message": "인증되지 않은 시크릿키 혹은 클라이언트키 입니다."
                }
                """;

        mockServer.expect(requestTo(CONFIRM_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body(errorBody)
                        .contentType(MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() ->
                tossPaymentService.confirmPayment("pk_test", "order_1", 10000))
                .isInstanceOf(TossPaymentException.class);
        mockServer.verify();
    }

    @Test
    @DisplayName("결제 승인 실패 - Toss API 5xx 응답")
    void confirmPayment_serverError_throwsTossPaymentException() {
        // given
        stubTossEnabled();
        mockServer.expect(requestTo(CONFIRM_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        // when & then
        assertThatThrownBy(() ->
                tossPaymentService.confirmPayment("pk_test", "order_1", 10000))
                .isInstanceOf(TossPaymentException.class);
        mockServer.verify();
    }

    @Test
    @DisplayName("Basic Auth 인코딩 - secretKey 뒤에 콜론 포함 확인")
    void confirmPayment_basicAuthContainsColon() {
        stubTossEnabled();
        // secretKey = "test_sk_abc123"
        // Base64("test_sk_abc123:") = "dGVzdF9za19hYmMxMjM6"
        String expectedAuth = "Basic dGVzdF9za19hYmMxMjM6";

        String responseBody = """
                {"paymentKey":"pk","orderId":"order","status":"DONE","totalAmount":10000,"method":"카드"}
                """;

        mockServer.expect(requestTo(CONFIRM_URL))
                .andExpect(header("Authorization", expectedAuth))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        tossPaymentService.confirmPayment("pk", "order", 10000);
        mockServer.verify();
    }

    @Test
    @DisplayName("직거래 모드에서는 Toss 결제 승인을 차단한다")
    void confirmPayment_directTradeMode_throwsServiceUnavailable() {
        when(matePaymentModeService.isDirectTrade()).thenReturn(true);

        assertThatThrownBy(() -> tossPaymentService.confirmPayment("pk", "order", 10000))
                .isInstanceOf(TossPaymentException.class)
                .hasMessageContaining("직거래 모드");
    }

    private void stubTossEnabled() {
        when(matePaymentModeService.isDirectTrade()).thenReturn(false);
        when(tossPaymentConfig.getSecretKey()).thenReturn(TEST_SECRET_KEY);
        when(tossPaymentConfig.getConfirmUrl()).thenReturn(CONFIRM_URL);
    }
}
