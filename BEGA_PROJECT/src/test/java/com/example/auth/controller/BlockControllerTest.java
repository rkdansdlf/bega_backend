package com.example.auth.controller;

import com.example.auth.dto.BlockToggleResponse;
import com.example.auth.dto.UserFollowSummaryDto;
import com.example.auth.service.BlockService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockControllerTest {

    @Mock
    private BlockService blockService;

    @InjectMocks
    private BlockController controller;

    @Test
    @DisplayName("차단 토글 시 응답을 반환한다")
    void toggleBlockByHandle_returnsResponse() {
        BlockToggleResponse resp = BlockToggleResponse.builder().blocked(true).build();
        when(blockService.toggleBlockByHandle("testuser")).thenReturn(resp);

        ResponseEntity<BlockToggleResponse> result = controller.toggleBlockByHandle("testuser");

        assertThat(result.getBody().isBlocked()).isTrue();
    }

    @Test
    @DisplayName("차단 목록을 조회한다")
    void getBlockedUsers_returnsPage() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<UserFollowSummaryDto> page = new PageImpl<>(List.of());
        when(blockService.getBlockedUsers(pageable)).thenReturn(page);

        ResponseEntity<Page<UserFollowSummaryDto>> result = controller.getBlockedUsers(pageable);

        assertThat(result.getBody()).isEqualTo(page);
    }
}
