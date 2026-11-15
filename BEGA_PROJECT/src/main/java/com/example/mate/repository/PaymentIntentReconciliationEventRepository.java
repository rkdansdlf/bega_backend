package com.example.mate.repository;

import com.example.mate.entity.PaymentIntentReconciliationEvent;
import com.example.mate.entity.PaymentIntentReconciliationEvent.EventType;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

public interface PaymentIntentReconciliationEventRepository
        extends Repository<PaymentIntentReconciliationEvent, Long> {

    PaymentIntentReconciliationEvent save(PaymentIntentReconciliationEvent event);

    Optional<PaymentIntentReconciliationEvent> findFirstByPaymentIntentIdAndEventTypeOrderByCreatedAtAsc(
            Long paymentIntentId,
            EventType eventType);

    boolean existsByPaymentIntentIdAndEventType(Long paymentIntentId, EventType eventType);

    List<PaymentIntentReconciliationEvent> findByPaymentIntentIdOrderByCreatedAtAscIdAsc(Long paymentIntentId);
}
