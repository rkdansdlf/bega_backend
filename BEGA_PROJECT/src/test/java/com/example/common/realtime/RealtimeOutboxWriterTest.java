package com.example.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;

class RealtimeOutboxWriterTest {

    private final RealtimeOutboxRepository repository = mock(RealtimeOutboxRepository.class);
    private final RealtimeOutboxWriter writer = new RealtimeOutboxWriter(repository, new ObjectMapper());

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void rejectsWritesOutsideAnActiveTransaction() {
        assertThatThrownBy(() -> writer.broadcast(
                "/topic/party/5",
                Map.of("message", "hello")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active transaction");

        verifyNoInteractions(repository);
    }

    @Test
    void storesValidatedPendingEnvelopeInsideTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        writer.sendToUser("42", "/queue/notifications", Map.of("id", 7));

        ArgumentCaptor<RealtimeOutboxEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeOutboxEvent.class);
        verify(repository).save(eventCaptor.capture());
        RealtimeOutboxEvent event = eventCaptor.getValue();
        assertThat(event.getEnvelopeVersion()).isEqualTo(RealtimeMessageEnvelope.CURRENT_VERSION);
        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getTarget()).isEqualTo(RealtimeMessageEnvelope.Target.USER);
        assertThat(event.getDestination()).isEqualTo("/queue/notifications");
        assertThat(event.getUserId()).isEqualTo("42");
        assertThat(event.getPayload()).contains("\"id\":7");
        assertThat(event.getStatus()).isEqualTo(RealtimeOutboxStatus.PENDING);
        assertThat(event.getAttemptCount()).isZero();
        assertThat(event.getAvailableAt()).isNotNull();
        assertThat(event.getCreatedAt()).isNotNull();
    }
}
