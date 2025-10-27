package com.example.cheerboard.config;

import com.example.cheerboard.domain.AppUser;
import com.example.cheerboard.repo.AppUserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Component
@RequestScope
public class CurrentUser {
    private final HttpServletRequest request;
    private final AppUserRepo userRepo;
    
    public CurrentUser(HttpServletRequest request, AppUserRepo userRepo) {
        this.request = request;
        this.userRepo = userRepo;
        System.out.println("🎯 CurrentUser 인스턴스 생성됨!");
    }

    private AppUser cached;

    public AppUser get() {
        if (cached != null) return cached;

        String email = headerOr("X-Debug-Email", "test@bega.app");
        String name  = decodeBase64Header(headerOr("X-Debug-Name",  "dGVzdA=="), "테스트");
        String team  = headerOr("X-Debug-Team",  "LG");
        String role  = headerOr("X-Debug-Role",  "USER");

        cached = userRepo.findByEmail(email).orElseGet(() -> {
            System.out.println("🆕 새 사용자 생성: " + email + ", 역할: " + role);
            return userRepo.save(AppUser.builder()
                .email(email).displayName(name).favoriteTeamId(team).role(role).build());
        });
        
        // 역할이 변경된 경우 업데이트 (개발 단계에서만)
        if (!cached.getRole().equals(role)) {
            System.out.println("🔄 역할 업데이트: " + cached.getRole() + " → " + role);
            cached.setRole(role);
            cached = userRepo.save(cached);
        }
        
        System.out.println("👤 현재 사용자: " + cached.getEmail() + ", 역할: " + cached.getRole());
        return cached;
    }

    private String headerOr(String key, String def) {
        try {
            String v = request.getHeader(key);
            return (v == null || v.isBlank()) ? def : v;
        } catch (Exception e) {
            return def;
        }
    }
    
    private String decodeBase64Header(String encoded, String def) {
        try {
            if (encoded == null || encoded.isBlank()) return def;
            byte[] decodedBytes = Base64.getDecoder().decode(encoded);
            String decoded = new String(decodedBytes, StandardCharsets.UTF_8);
            return URLDecoder.decode(decoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return def;
        }
    }
}