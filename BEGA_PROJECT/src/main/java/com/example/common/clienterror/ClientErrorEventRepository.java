package com.example.common.clienterror;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientErrorEventRepository
        extends JpaRepository<ClientErrorEventEntity, Long>, JpaSpecificationExecutor<ClientErrorEventEntity> {

    Optional<ClientErrorEventEntity> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    List<ClientErrorEventEntity> findByOccurredAtBetweenOrderByOccurredAtAsc(LocalDateTime from, LocalDateTime to);

    List<ClientErrorEventEntity> findByOccurredAtGreaterThanEqualOrderByOccurredAtAsc(LocalDateTime from);

    List<ClientErrorEventEntity> findTop10ByFingerprintAndEventIdNotOrderByOccurredAtDesc(String fingerprint, String eventId);

    @Query("""
            select count(distinct e.fingerprint)
            from ClientErrorEventEntity e
            where e.occurredAt between :from and :to
            """)
    long countDistinctFingerprints(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            select count(distinct e.normalizedRoute)
            from ClientErrorEventEntity e
            where e.occurredAt between :from and :to
              and e.normalizedRoute is not null
              and e.normalizedRoute <> ''
            """)
    long countDistinctRoutes(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    long countByOccurredAtBetweenAndBucket(LocalDateTime from, LocalDateTime to, ClientErrorBucket bucket);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ClientErrorEventEntity e
            set e.feedbackCount = coalesce(e.feedbackCount, 0) + 1
            where e.eventId = :eventId
            """)
    int incrementFeedbackCount(@Param("eventId") String eventId);

    long deleteByOccurredAtBefore(LocalDateTime cutoff);
}
