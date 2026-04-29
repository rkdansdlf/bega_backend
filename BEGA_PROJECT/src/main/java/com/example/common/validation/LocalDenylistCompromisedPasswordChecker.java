package com.example.common.validation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class LocalDenylistCompromisedPasswordChecker implements CompromisedPasswordChecker {

    private final Set<String> deniedPasswords;

    @Autowired
    public LocalDenylistCompromisedPasswordChecker(
            @Value("classpath:security/common-passwords.txt") Resource denylistResource) {
        this.deniedPasswords = loadDenylist(denylistResource);
    }

    LocalDenylistCompromisedPasswordChecker(Set<String> deniedPasswords) {
        this.deniedPasswords = deniedPasswords == null ? Set.of() : Set.copyOf(deniedPasswords);
    }

    @Override
    public boolean isCompromised(String password) {
        if (!StringUtils.hasText(password)) {
            return false;
        }
        return deniedPasswords.contains(normalize(password));
    }

    private Set<String> loadDenylist(Resource resource) {
        if (resource == null || !resource.exists()) {
            log.warn("Common password denylist resource is missing");
            return Set.of();
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("#"))
                    .map(this::normalize)
                    .collect(Collectors.collectingAndThen(Collectors.toSet(), Collections::unmodifiableSet));
        } catch (IOException e) {
            log.warn("Failed to load common password denylist", e);
            return Set.of();
        }
    }

    private String normalize(String password) {
        return password.trim().toLowerCase(Locale.ROOT);
    }
}
