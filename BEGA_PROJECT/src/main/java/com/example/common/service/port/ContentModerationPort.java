package com.example.common.service.port;

import java.util.Optional;

@FunctionalInterface
public interface ContentModerationPort {

    Optional<ContentModerationDecision> moderate(String content);
}
