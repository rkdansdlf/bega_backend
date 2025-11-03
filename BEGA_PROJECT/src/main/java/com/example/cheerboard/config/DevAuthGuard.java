package com.example.cheerboard.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(0)
public class DevAuthGuard extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
        throws java.io.IOException, ServletException {
        // 읽기는 누구나 허용, 쓰기(POST/DELETE/PATCH/PUT)만 헤더 요구
        String m = req.getMethod();
        boolean write = m.equals("POST") || m.equals("DELETE") || m.equals("PUT") || m.equals("PATCH");
        if (write && req.getRequestURI().startsWith("/api/cheer")) {
            if (isBlank(req.getHeader("X-Debug-Email")) || isBlank(req.getHeader("X-Debug-Team"))) {
                res.sendError(401, "DevAuth: X-Debug-Email / X-Debug-Team 헤더 필요");
                return;
            }
        }
        chain.doFilter(req, res);
    }
    
    private boolean isBlank(String s) { 
        return s == null || s.isBlank(); 
    }
}