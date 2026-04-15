package com.example.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AdminNonCanonicalCleanupTrackerUpsertRequest(
        @Size(max = 500) String ticketUrl,
        @Size(max = 120) String assignee,
        @Pattern(regexp = "draft|requested|in_progress|done") String status,
        @Size(max = 4000) String note,
        List<@Size(max = 64) String> gameIds
) {
}
