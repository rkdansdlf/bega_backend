package com.example.auth.service;

import jakarta.servlet.http.HttpServletRequest;

public interface CaptchaVerifier {
    boolean isEnabled();

    boolean verify(String token, HttpServletRequest request);
}
