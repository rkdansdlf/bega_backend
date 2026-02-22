package com.example.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIModerationService {

    @Value("${ai.service-url}")
    private String aiServiceUrl;

    @Value("${ai.moderation.high-risk-keywords:죽어,죽인다,살인,테러,시발,씨발,병신,개새끼}")
    private String highRiskKeywordsRaw;

    @Value("${ai.moderation.spam-keywords:광고,홍보,문의,오픈채팅,텔레그램,카카오톡,디엠,수익}")
    private String spamKeywordsRaw;

    @Value("${ai.moderation.spam-url-threshold:3}")
    private int spamUrlThreshold;

    @Value("${ai.moderation.repeat-char-threshold:8}")
    private int repeatCharThreshold;

    @Value("${ai.moderation.spam-medium-threshold:2}")
    private int spamMediumThreshold;

    @Value("${ai.moderation.spam-block-threshold:3}")
    private int spamBlockThreshold;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+|www\\.\\S+");

    public ModerationResult checkContent(String content) {
        if (content == null || content.isBlank()) {
            return ModerationResult.allow();
        }

        ModerationResult ruleResult = evaluateRuleBasedRisk(content);

        try {
            String url = aiServiceUrl + "/moderation/safety-check";
            Map<String, String> request = Map.of("content", content);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

            if (response == null) {
                log.warn("AI moderation response is null; using rule fallback.");
                return ruleResult.withDecisionSource("FALLBACK");
            }

            String category = asString(response.get("category"), "SAFE");
            String reason = asString(response.get("reason"), "");
            String action = asString(response.get("action"), "ALLOW");
            String decisionSource = asString(response.get("decisionSource"), "MODEL");
            String riskLevel = normalizeRiskLevel(asString(response.get("riskLevel"), "LOW"));

            ModerationResult modelResult = new ModerationResult(
                    category,
                    reason,
                    "ALLOW".equalsIgnoreCase(action),
                    decisionSource.toUpperCase(Locale.ROOT),
                    riskLevel);

            return mergeRuleAndModel(ruleResult, modelResult);
        } catch (Exception e) {
            log.error("AI moderation request failed. Using rule fallback.", e);
            return ruleResult.withDecisionSource("FALLBACK");
        }
    }

    private ModerationResult mergeRuleAndModel(ModerationResult ruleResult, ModerationResult modelResult) {
        if (!ruleResult.isAllowed()) {
            return ruleResult.withDecisionSource("RULE");
        }
        if (!modelResult.isAllowed()) {
            return modelResult.withDecisionSource("MODEL");
        }
        if ("MEDIUM".equals(ruleResult.riskLevel())) {
            return new ModerationResult(
                    "SPAM",
                    "주의 패턴이 감지되었습니다.",
                    true,
                    "RULE",
                    "MEDIUM");
        }
        return modelResult;
    }

    private ModerationResult evaluateRuleBasedRisk(String content) {
        String normalized = content.toLowerCase(Locale.ROOT);
        Set<String> highRiskKeywords = parseKeywordSet(highRiskKeywordsRaw);
        Set<String> spamKeywords = parseKeywordSet(spamKeywordsRaw);

        if (containsAny(normalized, highRiskKeywords)) {
            return new ModerationResult(
                    "INAPPROPRIATE",
                    "안전 정책에 따라 검토가 필요한 표현이 감지되었습니다.",
                    false,
                    "RULE",
                    "HIGH");
        }

        int spamScore = 0;
        int urlCount = countMatches(URL_PATTERN, normalized);
        if (urlCount >= Math.max(1, spamUrlThreshold)) {
            spamScore += 2;
        }

        if (hasRepeatedChars(normalized)) {
            spamScore += 2;
        }

        if (containsAny(normalized, spamKeywords)) {
            spamScore += 1;
        }

        if (spamScore >= Math.max(1, spamBlockThreshold)) {
            return new ModerationResult(
                    "SPAM",
                    "스팸 가능성이 높은 패턴이 감지되었습니다.",
                    false,
                    "RULE",
                    "HIGH");
        }

        if (spamScore >= Math.max(1, spamMediumThreshold)) {
            return new ModerationResult(
                    "SPAM",
                    "주의 패턴이 감지되었습니다.",
                    true,
                    "RULE",
                    "MEDIUM");
        }

        return new ModerationResult("SAFE", "", true, "RULE", "LOW");
    }

    private boolean containsAny(String content, Set<String> keywords) {
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private int countMatches(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private boolean hasRepeatedChars(String content) {
        int threshold = Math.max(2, repeatCharThreshold);
        Pattern repeatedPattern = Pattern.compile("(.)\\1{" + (threshold - 1) + ",}");
        return repeatedPattern.matcher(content).find();
    }

    private Set<String> parseKeywordSet(String rawKeywords) {
        if (rawKeywords == null || rawKeywords.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(rawKeywords.split(","))
                .map(String::trim)
                .filter(keyword -> !keyword.isBlank())
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private String asString(Object value, String defaultValue) {
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        return defaultValue;
    }

    private String normalizeRiskLevel(String riskLevel) {
        String normalized = riskLevel.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LOW", "MEDIUM", "HIGH" -> normalized;
            default -> "LOW";
        };
    }

    public record ModerationResult(
            String category,
            String reason,
            boolean isAllowed,
            String decisionSource,
            String riskLevel) {
        public static ModerationResult allow() {
            return new ModerationResult("SAFE", "", true, "RULE", "LOW");
        }

        public ModerationResult withDecisionSource(String source) {
            return new ModerationResult(
                    this.category,
                    this.reason,
                    this.isAllowed,
                    source,
                    this.riskLevel);
        }
    }
}
