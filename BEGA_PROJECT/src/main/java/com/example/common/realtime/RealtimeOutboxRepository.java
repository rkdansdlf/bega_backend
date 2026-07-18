package com.example.common.realtime;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RealtimeOutboxRepository extends JpaRepository<RealtimeOutboxEvent, Long> {

    @Query("""
            select event.id
            from RealtimeOutboxEvent event
            where ((event.status in :readyStatuses and event.availableAt <= :dueAt)
                or (event.status = :processingStatus and event.lockedUntil <= :leaseExpiredBefore))
            order by event.id
            """)
    List<Long> findClaimableIds(
            @Param("readyStatuses") Collection<RealtimeOutboxStatus> readyStatuses,
            @Param("processingStatus") RealtimeOutboxStatus processingStatus,
            @Param("dueAt") Instant dueAt,
            @Param("leaseExpiredBefore") Instant leaseExpiredBefore,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RealtimeOutboxEvent event
               set event.status = :processingStatus,
                   event.lockedBy = :workerId,
                   event.lockedUntil = :lockedUntil,
                   event.attemptCount = event.attemptCount + 1
             where event.id = :id
               and ((event.status in :readyStatuses and event.availableAt <= :dueAt)
                    or (event.status = :processingStatus and event.lockedUntil <= :leaseExpiredBefore))
            """)
    int claimIfAvailable(
            @Param("id") Long id,
            @Param("workerId") String workerId,
            @Param("dueAt") Instant dueAt,
            @Param("leaseExpiredBefore") Instant leaseExpiredBefore,
            @Param("lockedUntil") Instant lockedUntil,
            @Param("readyStatuses") Collection<RealtimeOutboxStatus> readyStatuses,
            @Param("processingStatus") RealtimeOutboxStatus processingStatus);

    default int claim(
            Long id,
            String workerId,
            Instant dueAt,
            Instant leaseExpiredBefore,
            Instant lockedUntil) {
        return claimIfAvailable(
                id,
                workerId,
                dueAt,
                leaseExpiredBefore,
                lockedUntil,
                List.of(RealtimeOutboxStatus.PENDING, RealtimeOutboxStatus.RETRY),
                RealtimeOutboxStatus.PROCESSING);
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RealtimeOutboxEvent event
               set event.status = :publishedStatus,
                   event.publishedAt = :publishedAt,
                   event.lockedBy = null,
                   event.lockedUntil = null,
                   event.lastError = null
             where event.id = :id
               and event.status = :processingStatus
               and event.lockedBy = :workerId
            """)
    int markPublished(
            @Param("id") Long id,
            @Param("workerId") String workerId,
            @Param("publishedAt") Instant publishedAt,
            @Param("processingStatus") RealtimeOutboxStatus processingStatus,
            @Param("publishedStatus") RealtimeOutboxStatus publishedStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RealtimeOutboxEvent event
               set event.status = :nextStatus,
                   event.availableAt = :availableAt,
                   event.lockedBy = null,
                   event.lockedUntil = null,
                   event.lastError = :lastError
             where event.id = :id
               and event.status = :processingStatus
               and event.lockedBy = :workerId
            """)
    int markFailure(
            @Param("id") Long id,
            @Param("workerId") String workerId,
            @Param("nextStatus") RealtimeOutboxStatus nextStatus,
            @Param("availableAt") Instant availableAt,
            @Param("lastError") String lastError,
            @Param("processingStatus") RealtimeOutboxStatus processingStatus);

    @Query("""
            select event.id
            from RealtimeOutboxEvent event
            where event.status = :publishedStatus
              and event.publishedAt < :cutoff
            order by event.id
            """)
    List<Long> findPublishedIdsBefore(
            @Param("publishedStatus") RealtimeOutboxStatus publishedStatus,
            @Param("cutoff") Instant cutoff,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from RealtimeOutboxEvent event
            where event.status = :publishedStatus
              and event.id in :ids
            """)
    int deletePublishedIds(
            @Param("publishedStatus") RealtimeOutboxStatus publishedStatus,
            @Param("ids") Collection<Long> ids);

    long countByStatusIn(Collection<RealtimeOutboxStatus> statuses);

    @Query("select min(event.createdAt) from RealtimeOutboxEvent event where event.status in :statuses")
    Instant findOldestCreatedAt(@Param("statuses") Collection<RealtimeOutboxStatus> statuses);
}
