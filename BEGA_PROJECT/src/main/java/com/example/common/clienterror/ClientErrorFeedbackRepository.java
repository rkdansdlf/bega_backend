package com.example.common.clienterror;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientErrorFeedbackRepository extends JpaRepository<ClientErrorFeedbackEntity, Long> {

    List<ClientErrorFeedbackEntity> findByOccurredAtBetweenOrderByOccurredAtAsc(LocalDateTime from, LocalDateTime to);

    List<ClientErrorFeedbackEntity> findTop10ByOccurredAtBetweenOrderByOccurredAtDesc(LocalDateTime from, LocalDateTime to);

    List<ClientErrorFeedbackEntity> findByEventIdOrderByOccurredAtDesc(String eventId);

    long countByOccurredAtBetween(LocalDateTime from, LocalDateTime to);

    long deleteByOccurredAtBefore(LocalDateTime cutoff);
}
