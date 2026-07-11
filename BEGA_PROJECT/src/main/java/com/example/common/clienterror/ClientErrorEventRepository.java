package com.example.common.clienterror;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
            select e.eventId as eventId,
                   e.bucket as bucket,
                   e.source as source,
                   e.message as message,
                   e.statusCode as statusCode,
                   e.statusGroup as statusGroup,
                   e.responseCode as responseCode,
                   e.route as route,
                   e.normalizedRoute as normalizedRoute,
                   e.method as method,
                   e.endpoint as endpoint,
                   e.normalizedEndpoint as normalizedEndpoint,
                   e.fingerprint as fingerprint,
                   e.occurredAt as occurredAt,
                   e.sessionId as sessionId,
                   e.userId as userId,
                   e.feedbackCount as feedbackCount
            from ClientErrorEventEntity e
            where e.fingerprint = :fingerprint
              and e.eventId <> :eventId
            order by e.occurredAt desc
            """)
    List<ClientErrorEventSummaryProjection> findRecentEventSummariesByFingerprint(
            @Param("fingerprint") String fingerprint,
            @Param("eventId") String eventId,
            Pageable pageable);

    @Query("""
            select e.fingerprint as fingerprint,
                   count(e.id) as observedCount,
                   max(e.occurredAt) as latestOccurredAt
            from ClientErrorEventEntity e
            where e.occurredAt >= :from
              and (
                    e.bucket = :runtimeBucket
                    or (e.bucket = :apiBucket and e.statusGroup = :apiAlertStatusGroup)
              )
            group by e.fingerprint
            having count(e.id) >= :minimumThreshold
            order by max(e.occurredAt) desc
            """)
    List<ClientErrorAlertCandidateProjection> findAlertCandidateSummaries(
            @Param("from") LocalDateTime from,
            @Param("runtimeBucket") ClientErrorBucket runtimeBucket,
            @Param("apiBucket") ClientErrorBucket apiBucket,
            @Param("apiAlertStatusGroup") String apiAlertStatusGroup,
            @Param("minimumThreshold") long minimumThreshold,
            Pageable pageable);

    @Query("""
            select e.eventId as eventId,
                   e.bucket as bucket,
                   e.source as source,
                   e.message as message,
                   e.statusCode as statusCode,
                   e.statusGroup as statusGroup,
                   e.responseCode as responseCode,
                   e.route as route,
                   e.normalizedRoute as normalizedRoute,
                   e.method as method,
                   e.endpoint as endpoint,
                   e.normalizedEndpoint as normalizedEndpoint,
                   e.fingerprint as fingerprint,
                   e.occurredAt as occurredAt,
                   e.sessionId as sessionId,
                   e.userId as userId,
                   e.feedbackCount as feedbackCount
            from ClientErrorEventEntity e
            where e.occurredAt >= :from
              and (
                    e.bucket = :runtimeBucket
                    or (e.bucket = :apiBucket and e.statusGroup = :apiAlertStatusGroup)
              )
              and e.fingerprint in :fingerprints
              and e.occurredAt = (
                    select max(latest.occurredAt)
                    from ClientErrorEventEntity latest
                    where latest.fingerprint = e.fingerprint
                      and latest.occurredAt >= :from
                      and (
                            latest.bucket = :runtimeBucket
                            or (latest.bucket = :apiBucket and latest.statusGroup = :apiAlertStatusGroup)
                      )
              )
            order by e.fingerprint asc, e.occurredAt desc, e.id desc
            """)
    List<ClientErrorEventSummaryProjection> findLatestAlertEventsByFingerprint(
            @Param("from") LocalDateTime from,
            @Param("runtimeBucket") ClientErrorBucket runtimeBucket,
            @Param("apiBucket") ClientErrorBucket apiBucket,
            @Param("apiAlertStatusGroup") String apiAlertStatusGroup,
            @Param("fingerprints") Collection<String> fingerprints);

    @Query("""
            select year(e.occurredAt) as bucketYear,
                   month(e.occurredAt) as bucketMonth,
                   day(e.occurredAt) as bucketDay,
                   hour(e.occurredAt) as bucketHour,
                   e.bucket as bucket,
                   count(e.id) as itemCount
            from ClientErrorEventEntity e
            where e.occurredAt between :from and :to
            group by year(e.occurredAt),
                     month(e.occurredAt),
                     day(e.occurredAt),
                     hour(e.occurredAt),
                     e.bucket
            """)
    List<ClientErrorEventTimeBucketProjection> countHourlyBuckets(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            select year(e.occurredAt) as bucketYear,
                   month(e.occurredAt) as bucketMonth,
                   day(e.occurredAt) as bucketDay,
                   0 as bucketHour,
                   e.bucket as bucket,
                   count(e.id) as itemCount
            from ClientErrorEventEntity e
            where e.occurredAt between :from and :to
            group by year(e.occurredAt),
                     month(e.occurredAt),
                     day(e.occurredAt),
                     e.bucket
            """)
    List<ClientErrorEventTimeBucketProjection> countDailyBuckets(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            select e.fingerprint as fingerprint,
                   count(e.id) as eventCount,
                   count(distinct case
                       when e.sessionId is not null and length(trim(e.sessionId)) > 0 then e.sessionId
                       else null
                   end) as uniqueSessions,
                   max(e.occurredAt) as latestOccurredAt
            from ClientErrorEventEntity e
            where e.occurredAt between :from and :to
            group by e.fingerprint
            order by count(e.id) desc, max(e.occurredAt) desc
            """)
    List<ClientErrorTopFingerprintProjection> findTopFingerprintSummaries(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query("""
            select count(distinct e.fingerprint) as distinctFingerprints,
                   count(distinct case
                       when e.normalizedRoute is not null and length(trim(e.normalizedRoute)) > 0 then e.normalizedRoute
                       else null
                   end) as distinctRoutes
            from ClientErrorEventEntity e
            where e.occurredAt between :from and :to
            """)
    ClientErrorDashboardDistinctTotalsProjection findDashboardDistinctTotals(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            select e.eventId as eventId,
                   e.bucket as bucket,
                   e.source as source,
                   e.message as message,
                   e.statusCode as statusCode,
                   e.statusGroup as statusGroup,
                   e.responseCode as responseCode,
                   e.route as route,
                   e.normalizedRoute as normalizedRoute,
                   e.method as method,
                   e.endpoint as endpoint,
                   e.normalizedEndpoint as normalizedEndpoint,
                   e.fingerprint as fingerprint,
                   e.occurredAt as occurredAt,
                   e.sessionId as sessionId,
                   e.userId as userId,
                   e.feedbackCount as feedbackCount
            from ClientErrorEventEntity e
            where e.occurredAt between :from and :to
              and e.fingerprint in :fingerprints
              and e.occurredAt = (
                    select max(latest.occurredAt)
                    from ClientErrorEventEntity latest
                    where latest.fingerprint = e.fingerprint
                      and latest.occurredAt between :from and :to
              )
            order by e.fingerprint asc, e.occurredAt desc, e.id desc
            """)
    List<ClientErrorEventSummaryProjection> findLatestEventsByFingerprintInBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("fingerprints") Collection<String> fingerprints);

    @Query(
            value = """
                    select e.eventId as eventId,
                           e.bucket as bucket,
                           e.source as source,
                           e.message as message,
                           e.statusCode as statusCode,
                           e.statusGroup as statusGroup,
                           e.responseCode as responseCode,
                           e.route as route,
                           e.normalizedRoute as normalizedRoute,
                           e.method as method,
                           e.endpoint as endpoint,
                           e.normalizedEndpoint as normalizedEndpoint,
                           e.fingerprint as fingerprint,
                           e.occurredAt as occurredAt,
                           e.sessionId as sessionId,
                           e.userId as userId,
                           e.feedbackCount as feedbackCount
                    from ClientErrorEventEntity e
                    where (:bucket is null or e.bucket = :bucket)
                      and (:source is null or e.source = :source)
                      and (:statusGroup is null or e.statusGroup = :statusGroup)
                      and (:normalizedRoute is null
                           or e.normalizedRoute like concat(concat('%', :normalizedRoute), '%'))
                      and (:fingerprint is null or e.fingerprint = :fingerprint)
                      and (:from is null or e.occurredAt >= :from)
                      and (:to is null or e.occurredAt <= :to)
                      and (:searchTerm is null or (
                            lower(e.eventId) like concat(concat('%', :searchTerm), '%')
                            or lower(e.message) like concat(concat('%', :searchTerm), '%')
                            or lower(e.route) like concat(concat('%', :searchTerm), '%')
                            or lower(e.endpoint) like concat(concat('%', :searchTerm), '%')
                            or lower(e.fingerprint) like concat(concat('%', :searchTerm), '%')
                      ))
                    order by e.occurredAt desc, e.id desc
                    """,
            countQuery = """
                    select count(e.id)
                    from ClientErrorEventEntity e
                    where (:bucket is null or e.bucket = :bucket)
                      and (:source is null or e.source = :source)
                      and (:statusGroup is null or e.statusGroup = :statusGroup)
                      and (:normalizedRoute is null
                           or e.normalizedRoute like concat(concat('%', :normalizedRoute), '%'))
                      and (:fingerprint is null or e.fingerprint = :fingerprint)
                      and (:from is null or e.occurredAt >= :from)
                      and (:to is null or e.occurredAt <= :to)
                      and (:searchTerm is null or (
                            lower(e.eventId) like concat(concat('%', :searchTerm), '%')
                            or lower(e.message) like concat(concat('%', :searchTerm), '%')
                            or lower(e.route) like concat(concat('%', :searchTerm), '%')
                            or lower(e.endpoint) like concat(concat('%', :searchTerm), '%')
                            or lower(e.fingerprint) like concat(concat('%', :searchTerm), '%')
                      ))
                    """)
    Page<ClientErrorEventSummaryProjection> findEventSummaries(
            @Param("bucket") ClientErrorBucket bucket,
            @Param("source") ClientErrorSource source,
            @Param("statusGroup") String statusGroup,
            @Param("normalizedRoute") String normalizedRoute,
            @Param("fingerprint") String fingerprint,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("searchTerm") String searchTerm,
            Pageable pageable);

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
