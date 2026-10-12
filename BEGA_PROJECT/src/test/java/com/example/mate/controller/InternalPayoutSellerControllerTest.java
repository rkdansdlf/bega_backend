package com.example.mate.controller;

import com.example.common.exception.GlobalExceptionHandler;
import com.example.mate.dto.SellerPayoutProfileDTO;
import com.example.mate.entity.SellerPayoutProfile;
import com.example.mate.service.SellerPayoutProfileService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalPayoutSellerControllerTest {

    @Mock
    private SellerPayoutProfileService sellerPayoutProfileService;

    @InjectMocks
    private InternalPayoutSellerController internalPayoutSellerController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(internalPayoutSellerController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getSellerProfile_returnsNotFoundWithStandardizedErrorBody() throws Exception {
        when(sellerPayoutProfileService.findByUserIdAndProvider(99L, "TOSS")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/internal/payout/sellers/99")
                        .param("provider", "TOSS")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SELLER_PAYOUT_PROFILE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("판매자 정산 매핑을 찾을 수 없습니다."));
    }

    @Test
    void getSellerProfile_returnsMappedProfileWhenPresent() throws Exception {
        SellerPayoutProfile profile = SellerPayoutProfile.builder()
                .id(10L)
                .userId(99L)
                .provider("TOSS")
                .providerSellerId("seller-123")
                .kycStatus("APPROVED")
                .metadataJson("{\"bank\":\"toss\"}")
                .createdAt(Instant.parse("2026-03-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-03-02T00:00:00Z"))
                .build();

        when(sellerPayoutProfileService.findByUserIdAndProvider(99L, "TOSS")).thenReturn(Optional.of(profile));

        mockMvc.perform(get("/api/internal/payout/sellers/99")
                        .param("provider", "TOSS")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("판매자 정산 매핑 조회 성공"))
                .andExpect(jsonPath("$.data.userId").value(99))
                .andExpect(jsonPath("$.data.provider").value("TOSS"))
                .andExpect(jsonPath("$.data.providerSellerId").value("seller-123"));
    }

    @Test
    void upsertSellerProfileRejectsProviderSellerIdLongerThan120AfterTrim() throws Exception {
        mockMvc.perform(post("/api/internal/payout/sellers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":10,\"provider\":\"TOSS\",\"providerSellerId\":\""
                                + "s".repeat(121) + "\",\"kycStatus\":\"APPROVED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.providerSellerId")
                        .value("providerSellerId는 공백 제거 후 120자 이하여야 합니다."));

        verifyNoInteractions(sellerPayoutProfileService);
    }

    @Test
    void upsertSellerProfileRejectsKycStatusLongerThan50() throws Exception {
        mockMvc.perform(post("/api/internal/payout/sellers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":10,\"provider\":\"TOSS\",\"providerSellerId\":\"seller_10\",\"kycStatus\":\""
                                + "k".repeat(51) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.kycStatus").value("kycStatus는 50자 이하여야 합니다."));

        verifyNoInteractions(sellerPayoutProfileService);
    }

    @Test
    void upsertSellerProfileAccepts120TrimmedIdAnd50CharacterKycStatus() throws Exception {
        String normalizedSellerId = "s".repeat(120);
        String rawSellerId = "  " + normalizedSellerId + "  ";
        String kycStatus = "k".repeat(50);
        SellerPayoutProfile profile = SellerPayoutProfile.builder()
                .id(10L)
                .userId(10L)
                .provider("TOSS")
                .providerSellerId(normalizedSellerId)
                .kycStatus(kycStatus)
                .build();
        when(sellerPayoutProfileService.upsert(any(SellerPayoutProfileDTO.UpsertRequest.class)))
                .thenReturn(profile);

        mockMvc.perform(post("/api/internal/payout/sellers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":10,\"provider\":\"TOSS\",\"providerSellerId\":\""
                                + rawSellerId + "\",\"kycStatus\":\"" + kycStatus + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerSellerId").value(normalizedSellerId))
                .andExpect(jsonPath("$.data.kycStatus").value(kycStatus));

        verify(sellerPayoutProfileService).upsert(argThat(request ->
                request.getProviderSellerId().equals(rawSellerId)
                        && request.getKycStatus().equals(kycStatus)));
    }
}
