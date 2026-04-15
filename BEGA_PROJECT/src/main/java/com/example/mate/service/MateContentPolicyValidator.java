package com.example.mate.service;

import com.example.common.exception.BadRequestBusinessException;

import java.util.List;
import java.util.regex.Pattern;

final class MateContentPolicyValidator {

    private static final List<String> FORBIDDEN_WORDS = List.of("욕설", "비방", "광고");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\d{3}[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s]+|www\\.[^\\s]+", Pattern.CASE_INSENSITIVE);

    private MateContentPolicyValidator() {
    }

    static void validatePartyDescription(String text) {
        String normalized = requireText(text, "INVALID_PARTY_DESCRIPTION", "소개글을 입력해주세요.");
        if (normalized.length() < 10 || normalized.length() > 200) {
            throw new BadRequestBusinessException(
                    "INVALID_PARTY_DESCRIPTION",
                    "소개글은 10자 이상 200자 이하로 입력해주세요.");
        }
        validateSharedContentPolicy(normalized, "INVALID_PARTY_DESCRIPTION", "연락처 정보나 링크는 입력할 수 없습니다. 매칭 후 채팅을 이용해주세요.");
    }

    static void validateApplicationMessage(String text) {
        String normalized = requireText(text, "INVALID_APPLICATION_MESSAGE", "신청 메시지를 입력해주세요.");
        if (normalized.length() < 10 || normalized.length() > 500) {
            throw new BadRequestBusinessException(
                    "INVALID_APPLICATION_MESSAGE",
                    "신청 메시지는 10자 이상 500자 이하로 입력해주세요.");
        }
        validateSharedContentPolicy(normalized, "INVALID_APPLICATION_MESSAGE", "연락처 정보나 링크는 입력할 수 없습니다. 매칭 후 채팅을 이용해주세요.");
    }

    static void validateChatMessage(String message, String imageUrl) {
        String normalizedMessage = message == null ? "" : message.trim();
        boolean hasMessage = !normalizedMessage.isEmpty();
        boolean hasImage = imageUrl != null && !imageUrl.isBlank();
        if (!hasMessage && !hasImage) {
            throw new BadRequestBusinessException("INVALID_CHAT_MESSAGE", "메시지 또는 이미지를 전송해주세요.");
        }
        if (!hasMessage) {
            return;
        }
        if (normalizedMessage.length() > 1000) {
            throw new BadRequestBusinessException("INVALID_CHAT_MESSAGE", "메시지는 1000자 이하여야 합니다.");
        }
        validateSharedContentPolicy(normalizedMessage, "INVALID_CHAT_MESSAGE", "개인정보 보호를 위해 연락처 정보나 외부 링크는 공유할 수 없습니다.");
    }

    private static String requireText(String text, String code, String message) {
        if (text == null || text.trim().isEmpty()) {
            throw new BadRequestBusinessException(code, message);
        }
        return text.trim();
    }

    private static void validateSharedContentPolicy(String text, String code, String contactMessage) {
        if (FORBIDDEN_WORDS.stream().anyMatch(text::contains)) {
            throw new BadRequestBusinessException(code, "부적절한 단어가 포함되어 있습니다.");
        }
        if (PHONE_PATTERN.matcher(text).find() || EMAIL_PATTERN.matcher(text).find() || URL_PATTERN.matcher(text).find()) {
            throw new BadRequestBusinessException(code, contactMessage);
        }
    }
}
