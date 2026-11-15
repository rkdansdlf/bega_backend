package com.example.mate.service;

import com.example.common.exception.BadRequestBusinessException;

/** Validates deterministic payment-backed application fields before provider confirmation. */
public final class PaymentApplicationInputPreflight {

    public static final String DEFAULT_APPLICATION_MESSAGE = "함께 즐거운 관람 부탁드립니다!";
    public static final int MAX_APPLICATION_MESSAGE_LENGTH = 500;
    public static final int MAX_TICKET_IMAGE_URL_LENGTH = 2048;

    private PaymentApplicationInputPreflight() {
    }

    public static String validateAndResolveMessage(String message, String ticketImageUrl) {
        String effectiveMessage = message == null || message.isBlank()
                ? DEFAULT_APPLICATION_MESSAGE
                : message;

        if (effectiveMessage.length() > MAX_APPLICATION_MESSAGE_LENGTH) {
            throw new BadRequestBusinessException(
                    "INVALID_APPLICATION_MESSAGE",
                    "신청 메시지는 500자 이하여야 합니다.");
        }
        MateContentPolicyValidator.validateApplicationMessage(effectiveMessage);

        if (ticketImageUrl != null && ticketImageUrl.length() > MAX_TICKET_IMAGE_URL_LENGTH) {
            throw new BadRequestBusinessException(
                    "INVALID_TICKET_IMAGE_URL",
                    "티켓 이미지 URL은 2048자 이하여야 합니다.");
        }

        return effectiveMessage;
    }
}
