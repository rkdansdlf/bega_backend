package com.example.BegaDiary.Entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SeatViewSubmissionRequest(
        @NotBlank @Size(max = 2048) String storagePath,
        @NotBlank @Size(max = 100) String stadium,
        @Size(max = 100) String section,
        @Size(max = 100) String block,
        @Size(max = 100) String seatRow,
        @Size(max = 100) String seatNumber,
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 140) String comment,
        @Size(max = 12) List<@NotBlank @Size(max = 40) String> tags) {
}
