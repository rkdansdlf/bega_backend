package com.example.mate.support;

import com.example.auth.entity.UserEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.security.Principal;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

public final class MateTestTokenHelper {

    private static final Map<String, Long> USER_IDS_BY_EMAIL = new ConcurrentHashMap<>();

    private MateTestTokenHelper() {
    }

    public static void register(UserEntity user) {
        if (user != null && user.getEmail() != null && user.getId() != null) {
            USER_IDS_BY_EMAIL.put(user.getEmail().toLowerCase(Locale.ROOT), user.getId());
        }
    }

    public static Principal principal(String email) {
        return () -> email;
    }

    public static RequestPostProcessor principalAs(String email) {
        Long userId = email == null ? null : USER_IDS_BY_EMAIL.get(email.toLowerCase(Locale.ROOT));
        if (userId != null) {
            var auth = UsernamePasswordAuthenticationToken.authenticated(
                    userId,
                    null,
                    AuthorityUtils.createAuthorityList("ROLE_USER"));
            return request -> {
                var processed = authentication(auth).postProcessRequest(request);
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(auth);
                SecurityContextHolder.setContext(context);
                processed.setUserPrincipal(auth);
                return processed;
            };
        }
        return request -> {
            request.setUserPrincipal(principal(email));
            return request;
        };
    }
}
