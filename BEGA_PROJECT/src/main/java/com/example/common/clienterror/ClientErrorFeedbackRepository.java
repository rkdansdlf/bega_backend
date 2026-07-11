package com.example.common.clienterror;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientErrorFeedbackRepository extends JpaRepository<ClientErrorFeedbackEntity, Long> {

    List<ClientErrorFeedbackEntity> findByOccurredAtBetweenOrderByOccurredAtAsc(LocalDateTime from, LocalDateTime to);

    List<ClientErrorFeedbackEntity> findTop10ByOccurredAtBetweenOrderByOccurredAtDesc(LocalDateTime from, LocalDateTime to);

    @Query("""
            select year(f.occurredAt) as bucketYear,
                   month(f.occurredAt) as bucketMonth,
                   day(f.occurredAt) as bucketDay,
                   hour(f.occurredAt) as bucketHour,
                   count(f.id) as itemCount
            from ClientErrorFeedbackEntity f
            where f.occurredAt between :from and :to
            group by year(f.occurredAt),
                     month(f.occurredAt),
                     day(f.occurredAt),
                     hour(f.occurredAt)
            """)
    List<ClientErrorFeedbackTimeBucketProjection> countHourlyBuckets(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            select year(f.occurredAt) as bucketYear,
                   month(f.occurredAt) as bucketMonth,
                   day(f.occurredAt) as bucketDay,
                   0 as bucketHour,
                   count(f.id) as itemCount
            from ClientErrorFeedbackEntity f
            where f.occurredAt between :from and :to
            group by year(f.occurredAt),
                     month(f.occurredAt),
                     day(f.occurredAt)
            """)
    List<ClientErrorFeedbackTimeBucketProjection> countDailyBuckets(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    List<ClientErrorFeedbackEntity> findByEventIdOrderByOccurredAtDesc(String eventId);

    long countByOccurredAtBetween(LocalDateTime from, LocalDateTime to);

    long deleteByOccurredAtBefore(LocalDateTime cutoff);
}
