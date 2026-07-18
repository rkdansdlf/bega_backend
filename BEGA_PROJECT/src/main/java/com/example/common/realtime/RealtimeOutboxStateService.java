package com.example.common.realtime;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RealtimeOutboxStateService {

    private static final List<RealtimeOutboxStatus> CLAIMABLE_STATUSES =
            List.of(RealtimeOutboxStatus.PENDING, RealtimeOutboxStatus.RETRY);
    private static final List<RealtimeOutboxStatus> ACTIVE_STATUSES =
            List.of(
                    RealtimeOutboxStatus.PENDING,
                    RealtimeOutboxStatus.PROCESSING,
                    RealtimeOutboxStatus.RETRY);

    private final RealtimeOutboxRepository repository;
    private final RealtimeOutboxProperties properties;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<Long> findClaimableIds(Instant now, int batchSize) {
        Duration clockSkew = clockSkew();
        return repository.findClaimableIds(
                CLAIMABLE_STATUSES,
                RealtimeOutboxStatus.PROCESSING,
                now,
                now.minus(clockSkew),
                PageRequest.of(0, Math.max(1, batchSize)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<RealtimeOutboxClaim> claim(
            Long id,
            String workerId,
            Instant now,
            Duration leaseDuration) {
        Duration clockSkew = clockSkew();
        int claimed = repository.claim(
                id,
                workerId,
                now,
                now.minus(clockSkew),
                now.plus(leaseDuration).plus(clockSkew));
        if (claimed == 0) {
            return Optional.empty();
        }
        return repository.findById(id).map(RealtimeOutboxClaim::from);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markPublished(Long id, String workerId, Instant publishedAt) {
        return repository.markPublished(
                id,
                workerId,
                publishedAt,
                RealtimeOutboxStatus.PROCESSING,
                RealtimeOutboxStatus.PUBLISHED) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailure(
            Long id,
            String workerId,
            RealtimeOutboxStatus nextStatus,
            Instant availableAt,
            String lastError) {
        return repository.markFailure(
                id,
                workerId,
                nextStatus,
                availableAt,
                lastError,
                RealtimeOutboxStatus.PROCESSING) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cleanupPublishedBefore(Instant cutoff, int batchSize) {
        List<Long> ids = repository.findPublishedIdsBefore(
                RealtimeOutboxStatus.PUBLISHED,
                cutoff,
                PageRequest.of(0, Math.max(1, batchSize)));
        if (ids.isEmpty()) {
            return 0;
        }
        return repository.deletePublishedIds(RealtimeOutboxStatus.PUBLISHED, ids);
    }

    @Transactional(readOnly = true)
    public long countActive() {
        return repository.countByStatusIn(ACTIVE_STATUSES);
    }

    @Transactional(readOnly = true)
    public Instant findOldestActiveCreatedAt() {
        return repository.findOldestCreatedAt(ACTIVE_STATUSES);
    }

    private Duration clockSkew() {
        Duration configured = properties.getLeaseClockSkew();
        return configured == null || configured.isNegative() ? Duration.ZERO : configured;
    }
}
