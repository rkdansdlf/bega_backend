package com.example.homepage;

import com.example.common.exception.GlobalExceptionHandler;
import com.example.kbo.validation.ManualBaseballDataMissingItem;
import com.example.kbo.validation.ManualBaseballDataRequest;
import com.example.kbo.validation.ManualBaseballDataRequiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OffseasonControllerManualDataRequiredTest {

    private MockMvc mockMvc;
    private OffseasonService offseasonService;

    @BeforeEach
    void setUp() {
        offseasonService = mock(OffseasonService.class);
        OffseasonController controller = new OffseasonController(offseasonService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getMetadataReturnsManualDataContractAndKeepsRequestedYear() throws Exception {
        given(offseasonService.getOffseasonMetadata(2024))
                .willThrow(manualDataRequired(2024));

        mockMvc.perform(get("/api/kbo/offseason/metadata").param("year", "2024"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MANUAL_BASEBALL_DATA_REQUIRED"))
                .andExpect(jsonPath("$.data.scope").value("kbo.offseason.metadata"))
                .andExpect(jsonPath("$.data.blocking").value(true))
                .andExpect(jsonPath("$.data.missingItems[0].key").value("season_metadata"))
                .andExpect(jsonPath("$.data.operatorMessage").value("요청 시즌=2024의 수상·포스트시즌 자료가 필요합니다."));

        verify(offseasonService).getOffseasonMetadata(2024);
    }

    @Test
    void getMetadataKeepsExistingDefaultYear() throws Exception {
        given(offseasonService.getOffseasonMetadata(2025))
                .willThrow(manualDataRequired(2025));

        mockMvc.perform(get("/api/kbo/offseason/metadata"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MANUAL_BASEBALL_DATA_REQUIRED"));

        verify(offseasonService).getOffseasonMetadata(2025);
    }

    private ManualBaseballDataRequiredException manualDataRequired(int year) {
        return new ManualBaseballDataRequiredException(new ManualBaseballDataRequest(
                "kbo.offseason.metadata",
                List.of(new ManualBaseballDataMissingItem(
                        "season_metadata",
                        "시즌 수상·포스트시즌 결과",
                        "검증된 내부 시즌 자료가 연결되지 않았습니다.",
                        "year=" + year + " with operator-provided season result rows")),
                "요청 시즌=" + year + "의 수상·포스트시즌 자료가 필요합니다.",
                true));
    }
}
