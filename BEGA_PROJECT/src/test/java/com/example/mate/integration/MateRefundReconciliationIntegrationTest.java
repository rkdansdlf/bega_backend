package com.example.mate.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.mate.dto.TossPaymentDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.repository.CheckInRecordRepository;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import com.example.mate.service.TossPaymentService;
import com.example.mate.support.MateTestFixtureFactory;
import com.example.mate.support.MateTestTokenHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.jwt.secret=test-jwt-secret-64-characters-long-for-hs512-signature-tests-key-1234567890",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:mate_refund_reconciliation;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "jobrunr.background-job-server.enabled=false",
        "jobrunr.dashboard.enabled=false",
        "mate.payment.mode=TOSS_TEST",
        "payment.payout.enabled=false",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1"
})
class MateRefundReconciliationIntegrationTest {

    private static final String HOST_EMAIL = "mate-refund-host@example.com";
    private static final String APPLICANT_EMAIL = "mate-refund-applicant@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProviderRepository userProviderRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private PartyApplicationRepository partyApplicationRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private CheckInRecordRepository checkInRecordRepository;

    @MockitoBean
    private TossPaymentService tossPaymentService;

    @MockitoBean
    private CheerPostRepo cheerPostRepo;

    @MockitoBean
    private CheerReportRepo cheerReportRepo;

    @BeforeEach
    void setUp() {
        checkInRecordRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        partyApplicationRepository.deleteAll();
        partyRepository.deleteAll();
        userProviderRepository.deleteAll();
        userRepository.deleteAll();

        UserEntity host = userRepository.save(MateTestFixtureFactory.user(HOST_EMAIL, "Refund Host"));
        UserEntity applicant = userRepository.save(MateTestFixtureFactory.user(APPLICANT_EMAIL, "Refund Applicant"));
        MateTestTokenHelper.register(host);
        MateTestTokenHelper.register(applicant);
        userProviderRepository.save(MateTestFixtureFactory.socialProvider(host, "kakao"));
        userProviderRepository.save(MateTestFixtureFactory.socialProvider(applicant, "naver"));
    }

    @Test
    @DisplayName("취소 API는 제공자 실제 취소 금액을 저장하고 신청자를 결제 사유 변조로부터 보호한다")
    void cancelApplication_reconcilesProviderActualAmount() throws Exception {
        Fixture fixture = savePaidApplication();
        TossPaymentDTO.CancelResponse providerResponse = new TossPaymentDTO.CancelResponse(
                fixture.paymentKey(), "DONE", fixture.grossAmount());
        providerResponse.setBalanceAmount(0);
        TossPaymentDTO.ConfirmResponse beforeCancel = new TossPaymentDTO.ConfirmResponse(
                fixture.paymentKey(), fixture.orderId(), "DONE", fixture.grossAmount(), "카드");
        beforeCancel.setBalanceAmount(fixture.grossAmount());
        given(tossPaymentService.getPayment(fixture.paymentKey())).willReturn(beforeCancel);
        given(tossPaymentService.cancelPayment(anyString(), anyString(), anyInt()))
                .willReturn(providerResponse);

        String responseJson = mockMvc.perform(post("/api/applications/{applicationId}/cancel", fixture.applicationId())
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "cancelReasonType", "SYSTEM",
                                "cancelMemo", "개인 사정"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(responseJson);
        assertThat(response.get("paymentStatus").asText()).isEqualTo("CANCELED");
        assertThat(response.get("refundAmount").asInt()).isEqualTo(fixture.grossAmount());
        assertThat(partyApplicationRepository.findById(fixture.applicationId())).isEmpty();

        PaymentTransaction transaction = paymentTransactionRepository
                .findByOrderId(fixture.orderId())
                .orElseThrow();
        assertThat(transaction.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(transaction.getRefundAmount()).isEqualTo(fixture.grossAmount());
        assertThat(transaction.getFeeAmount()).isZero();
        verify(tossPaymentService).cancelPayment(
                fixture.paymentKey(),
                "메이트 취소 처리: BUYER_CHANGED_MIND",
                19800);
    }

    @Test
    @DisplayName("제공자 취소 금액이 의도보다 작으면 신청과 환불 상태를 성공으로 확정하지 않는다")
    void cancelApplication_failsClosedWhenProviderAmountIsShort() throws Exception {
        Fixture fixture = savePaidApplication();
        TossPaymentDTO.CancelResponse providerResponse = new TossPaymentDTO.CancelResponse(
                fixture.paymentKey(), "PARTIAL_CANCELED", fixture.grossAmount());
        providerResponse.setBalanceAmount(5000);
        TossPaymentDTO.ConfirmResponse lookupResponse = new TossPaymentDTO.ConfirmResponse(
                fixture.paymentKey(), fixture.orderId(), "PARTIAL_CANCELED", fixture.grossAmount(), "카드");
        lookupResponse.setBalanceAmount(5000);
        given(tossPaymentService.cancelPayment(anyString(), anyString(), anyInt()))
                .willReturn(providerResponse);
        given(tossPaymentService.getPayment(fixture.paymentKey())).willReturn(lookupResponse);

        mockMvc.perform(post("/api/applications/{applicationId}/cancel", fixture.applicationId())
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "cancelReasonType", "BUYER_CHANGED_MIND",
                                "cancelMemo", "환불 확인"))))
                .andExpect(status().is5xxServerError());

        assertThat(partyApplicationRepository.findById(fixture.applicationId())).isPresent();
        PaymentTransaction failedTransaction = paymentTransactionRepository
                .findByOrderId(fixture.orderId())
                .orElseThrow();
        assertThat(failedTransaction.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_FAILED);
        assertThat(failedTransaction.getRequestedRefundAmount()).isEqualTo(19800);
        assertThat(failedTransaction.getRequestedFeeAmount()).isEqualTo(2200);
        assertThat(failedTransaction.getCancellationRequestedAt()).isNotNull();
    }

    private Fixture savePaidApplication() {
        UserEntity host = userRepository.findByEmail(HOST_EMAIL).orElseThrow();
        UserEntity applicant = userRepository.findByEmail(APPLICANT_EMAIL).orElseThrow();
        Party party = partyRepository.save(MateTestFixtureFactory.pendingParty(host.getId(), host.getName(), 3));
        String orderId = "MATE-REFUND-" + party.getId();
        String paymentKey = "refund-pay-key-" + party.getId();
        PartyApplication application = partyApplicationRepository.save(
                MateTestFixtureFactory.application(
                        party.getId(),
                        applicant.getId(),
                        applicant.getName(),
                        PartyApplication.PaymentType.DEPOSIT,
                        true,
                        false,
                        false,
                        orderId,
                        paymentKey));
        int grossAmount = application.getDepositAmount();
        paymentTransactionRepository.save(PaymentTransaction.builder()
                .partyId(party.getId())
                .applicationId(application.getId())
                .buyerUserId(applicant.getId())
                .sellerUserId(host.getId())
                .flowType(PaymentFlowType.DEPOSIT)
                .orderId(orderId)
                .paymentKey(paymentKey)
                .grossAmount(grossAmount)
                .feeAmount(0)
                .refundAmount(0)
                .netAmount(grossAmount)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.PENDING)
                .build());
        return new Fixture(application.getId(), orderId, paymentKey, grossAmount);
    }

    private record Fixture(Long applicationId, String orderId, String paymentKey, int grossAmount) {
    }
}
