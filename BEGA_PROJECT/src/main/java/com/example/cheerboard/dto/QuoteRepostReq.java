package com.example.cheerboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 인용 리포스트 요청 DTO
 * 원글을 첨부하면서 의견(코멘트)을 덧붙여 작성할 때 사용
 */
public record QuoteRepostReq(
                @NotBlank(message = "인용 리포스트에는 내용이 필요합니다.") @Size(max = 500, message = "내용은 500자를 초과할 수 없습니다.") String content) {
}
