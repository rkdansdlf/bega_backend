package com.example.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.dto.ApiResponse;
import com.example.media.dto.MediaBackfillDomainReport;
import com.example.media.dto.MediaBackfillIssueSample;
import com.example.media.dto.MediaBackfillReport;
import com.example.media.dto.MediaCleanupReport;
import com.example.media.dto.MediaCleanupTargetReport;
import com.example.media.dto.MediaSmokeDomainReport;
import com.example.media.dto.MediaSmokeReport;
import com.example.media.entity.MediaCleanupTarget;
import com.example.media.entity.MediaDomain;
import com.example.media.service.MediaMaintenanceService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AdminMediaMaintenanceControllerTest {

    @Mock
    private MediaMaintenanceService mediaMaintenanceService;

    @InjectMocks
    private AdminMediaMaintenanceController controller;

    @Test
    @DisplayName("media smoke maintenance endpoint는 report를 반환한다")
    void runSmoke_returnsReport() {
        when(mediaMaintenanceService.runSmoke(7, List.of(MediaDomain.CHEER))).thenReturn(new MediaSmokeReport(
                7,
                List.of("CHEER"),
                List.of(new MediaSmokeDomainReport(MediaDomain.CHEER, 3, 0, 0, 0, List.of())),
                false));

        ResponseEntity<ApiResponse<MediaSmokeReport>> result = controller.runSmoke(7, "cheer");

        assertThat(result.getBody().isSuccess()).isTrue();
        verify(mediaMaintenanceService).runSmoke(7, List.of(MediaDomain.CHEER));
    }

    @Test
    @DisplayName("media backfill maintenance endpoint는 dry-run/apply report를 반환한다")
    void backfillExistingData_returnsReport() {
        when(mediaMaintenanceService.backfillExistingData(true, 200, List.of(MediaDomain.PROFILE), true)).thenReturn(new MediaBackfillReport(
                true,
                200,
                List.of("PROFILE"),
                List.of(new MediaBackfillDomainReport(
                        "PROFILE",
                        1,
                        1,
                        1,
                        0,
                        1,
                        0,
                        0,
                        List.of(),
                        List.of(),
                        List.of(),
                        Map.of("LEGACY_OWNER_MISMATCH", 1),
                        List.of(new MediaBackfillIssueSample(
                                "LEGACY_OWNER_MISMATCH",
                                "userId=7:profile",
                                "profiles/8/avatar.webp",
                                "media key guard rejected key")))),
                false));

        ResponseEntity<ApiResponse<MediaBackfillReport>> result = controller.backfillExistingData(true, 200, "profile", true);

        assertThat(result.getBody().isSuccess()).isTrue();
        verify(mediaMaintenanceService).backfillExistingData(true, 200, List.of(MediaDomain.PROFILE), true);
    }

    @Test
    @DisplayName("media cleanup maintenance endpoint는 cleanup report를 반환한다")
    void runCleanup_returnsReport() {
        when(mediaMaintenanceService.runCleanup(List.of(MediaCleanupTarget.PENDING, MediaCleanupTarget.ORPHAN))).thenReturn(
                new MediaCleanupReport(
                        List.of("PENDING", "ORPHAN"),
                        List.of(
                                new MediaCleanupTargetReport(MediaCleanupTarget.PENDING, 3, 3, 0),
                                new MediaCleanupTargetReport(MediaCleanupTarget.ORPHAN, 1, 1, 0)),
                        false));

        ResponseEntity<ApiResponse<MediaCleanupReport>> result = controller.runCleanup("pending,orphan");

        assertThat(result.getBody().isSuccess()).isTrue();
        verify(mediaMaintenanceService).runCleanup(List.of(MediaCleanupTarget.PENDING, MediaCleanupTarget.ORPHAN));
    }
}
