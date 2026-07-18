package com.example.common.realtime.authorization;

import java.util.Optional;
import java.util.function.BooleanSupplier;

import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimeAuthorizationService {

    private final RealtimeAuthorizationCache cache;
    private final RealtimeAuthorizationPolicyEvaluator evaluator;
    private final MeterRegistry meterRegistry;

    public boolean canAccessParty(Long partyId, Long userId) {
        return decide(
                RealtimeAuthorizationResource.PARTY,
                partyId,
                userId,
                () -> evaluator.canAccessParty(partyId, userId));
    }

    public boolean canAccessDmRoom(Long roomId, Long userId) {
        return decide(
                RealtimeAuthorizationResource.DM,
                roomId,
                userId,
                () -> evaluator.canAccessDmRoom(roomId, userId));
    }

    private boolean decide(
            RealtimeAuthorizationResource resource,
            Long resourceId,
            Long userId,
            BooleanSupplier policy) {
        Optional<Boolean> cached = cache.get(resource, resourceId, userId);
        if (cached.isPresent()) {
            boolean allowed = cached.get();
            recordDecision(resource, allowed ? "allow" : "deny");
            return allowed;
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        final boolean allowed;
        try {
            allowed = policy.getAsBoolean();
        } catch (RuntimeException exception) {
            recordDecision(resource, "error");
            log.error(
                    "Realtime authorization database evaluation failed resource={} resourceId={} userId={}",
                    resource.tag(), resourceId, userId, exception);
            throw new RealtimeAuthorizationUnavailableException(
                    "Realtime authorization policy is unavailable",
                    exception);
        } finally {
            sample.stop(Timer.builder("realtime.authorization.db.duration")
                    .tag("resource", resource.tag())
                    .register(meterRegistry));
        }

        cache.put(resource, resourceId, userId, allowed);
        recordDecision(resource, allowed ? "allow" : "deny");
        return allowed;
    }

    private void recordDecision(RealtimeAuthorizationResource resource, String outcome) {
        meterRegistry.counter(
                "realtime.authorization.decision",
                "resource", resource.tag(),
                "outcome", outcome).increment();
    }
}
