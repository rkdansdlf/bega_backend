package com.example.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class OffseasonMovementAdminRequest {

    @NotNull(message = "이동 날짜는 필수입니다.")
    private LocalDate movementDate;

    @NotBlank(message = "구분은 필수입니다.")
    @Size(max = 50, message = "구분은 50자 이하여야 합니다.")
    private String section;

    @NotBlank(message = "팀 코드는 필수입니다.")
    @Size(max = 20, message = "팀 코드는 20자 이하여야 합니다.")
    private String teamCode;

    @NotBlank(message = "선수명은 필수입니다.")
    @Size(max = 100, message = "선수명은 100자 이하여야 합니다.")
    private String playerName;

    @Size(max = 300, message = "요약은 300자 이하여야 합니다.")
    private String summary;

    @Size(max = 4000, message = "상세 메모는 4000자 이하여야 합니다.")
    private String details;

    @Size(max = 100, message = "계약 기간은 100자 이하여야 합니다.")
    private String contractTerm;

    @Size(max = 120, message = "계약 규모는 120자 이하여야 합니다.")
    private String contractValue;

    @Size(max = 300, message = "옵션 정보는 300자 이하여야 합니다.")
    private String optionDetails;

    @Size(max = 50, message = "상대 구단은 50자 이하여야 합니다.")
    private String counterpartyTeam;

    @Size(max = 500, message = "반대급부 정보는 500자 이하여야 합니다.")
    private String counterpartyDetails;

    @Size(max = 100, message = "출처명은 100자 이하여야 합니다.")
    private String sourceLabel;

    @Size(max = 500, message = "출처 URL은 500자 이하여야 합니다.")
    private String sourceUrl;

    private LocalDateTime announcedAt;
}
