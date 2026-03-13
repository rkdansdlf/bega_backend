package com.example.mate.controller;

import com.example.common.exception.GlobalExceptionHandler;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
