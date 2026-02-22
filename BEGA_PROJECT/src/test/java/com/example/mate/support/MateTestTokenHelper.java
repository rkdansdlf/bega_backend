package com.example.mate.support;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.security.Principal;

public final class MateTestTokenHelper {

    private MateTestTokenHelper() {
    }

    public static Principal principal(String email) {
        return () -> email;
    }

    public static RequestPostProcessor principalAs(String email) {
        return request -> {
            request.setUserPrincipal(principal(email));
            return request;
        };
    }
}
