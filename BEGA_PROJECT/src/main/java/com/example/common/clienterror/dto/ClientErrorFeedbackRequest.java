package com.example.common.clienterror.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientErrorFeedbackRequest(
        @NotBlank @Size(max = 64) String eventId,
        @NotBlank @Size(max = 2000) String comment,
        @NotBlank @Size(max = 64) String actionTaken,
        @NotBlank @Size(max = 500) String route,
        @NotBlank @Size(max = 64) String timestamp) {
}
