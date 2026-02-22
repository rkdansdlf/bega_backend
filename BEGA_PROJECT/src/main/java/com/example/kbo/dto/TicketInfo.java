package com.example.kbo.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketInfo {
    private String date;
    private String time;
    private String stadium;
    private String homeTeam;
    private String awayTeam;
    private String section;
    private String row;
    private String seat;
    private Integer peopleCount;
    private Integer price;
    private String reservationNumber;
    private Long gameId;
    private String verificationToken; // Server-generated proof of successful OCR
}
