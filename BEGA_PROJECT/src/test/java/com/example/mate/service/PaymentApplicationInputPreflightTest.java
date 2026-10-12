package com.example.mate.service;

import com.example.common.exception.BadRequestBusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentApplicationInputPreflightTest {

    @Test
    void resolvesBlankMessageAndAcceptsExistingContractBoundaries() {
        String ticketImageUrl = "x".repeat(PaymentApplicationInputPreflight.MAX_TICKET_IMAGE_URL_LENGTH);

        String message = PaymentApplicationInputPreflight.validateAndResolveMessage("  ", ticketImageUrl);

        assertThat(message).isEqualTo(PaymentApplicationInputPreflight.DEFAULT_APPLICATION_MESSAGE);
        assertThat(ticketImageUrl).hasSize(2048);
    }

    @Test
    void blankMessagesUseTheExistingDefaultBeforeLengthValidation() {
        String message = PaymentApplicationInputPreflight.validateAndResolveMessage(" ".repeat(2049), null);

        assertThat(message).isEqualTo(PaymentApplicationInputPreflight.DEFAULT_APPLICATION_MESSAGE);
    }

    @Test
    void acceptsMessageAtTheExisting500CharacterLimit() {
        String message = "가".repeat(PaymentApplicationInputPreflight.MAX_APPLICATION_MESSAGE_LENGTH);

        assertThat(PaymentApplicationInputPreflight.validateAndResolveMessage(message, null)).isEqualTo(message);
    }

    @Test
    void rejectsMessageOutsideRawStorageLimitEvenWhenTrimmedContentIsShort() {
        String message = "유효한 신청 메시지입니다." + " ".repeat(500);

        assertThatThrownBy(() -> PaymentApplicationInputPreflight.validateAndResolveMessage(message, null))
                .isInstanceOfSatisfying(BadRequestBusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_APPLICATION_MESSAGE"));
    }

    @Test
    void rejectsMessageThatFailsExistingContentPolicy() {
        assertThatThrownBy(() -> PaymentApplicationInputPreflight.validateAndResolveMessage("short", null))
                .isInstanceOfSatisfying(BadRequestBusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_APPLICATION_MESSAGE"));
    }

    @Test
    void rejectsTicketImageUrlOutsideThePreservedContract() {
        assertThatThrownBy(() -> PaymentApplicationInputPreflight.validateAndResolveMessage(
                "유효한 신청 메시지입니다.", "x".repeat(2049)))
                .isInstanceOfSatisfying(BadRequestBusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_TICKET_IMAGE_URL"));
    }
}
