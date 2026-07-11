package com.example.stadium.service;

public record StadiumPlaceCommand(
        String name,
        String category,
        String description,
        String address,
        String phone,
        Double lat,
        Double lng,
        Double rating,
        String openTime,
        String closeTime) {
}
