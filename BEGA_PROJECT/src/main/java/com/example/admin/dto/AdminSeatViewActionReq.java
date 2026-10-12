package com.example.admin.dto;

import jakarta.validation.constraints.Size;

public record AdminSeatViewActionReq(
        String adminLabel,
        String moderationStatus,
        @Size(max = 1000, message = "시야뷰 검수 메모는 1000자 이하여야 합니다.")
        String adminMemo) {
}
