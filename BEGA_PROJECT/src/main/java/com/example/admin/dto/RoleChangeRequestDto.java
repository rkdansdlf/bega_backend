package com.example.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 권한 변경 요청 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleChangeRequestDto {

    /**
     * 변경 사유 (선택)
     */
    @Size(max = 500, message = "변경 사유는 500자 이하여야 합니다.")
    private String reason;
}
