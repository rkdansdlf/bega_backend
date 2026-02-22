package com.example.cheerboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentReq(
        @NotBlank(message = "댓글 내용을 입력해주세요.")
        @Size(max = 2000, message = "댓글은 최대 2000자까지 입력할 수 있습니다.")
        String content
) {}
