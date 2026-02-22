package com.example.mate.repository;

import com.example.mate.entity.PaymentIntent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, Long> {

    Optional<PaymentIntent> findByOrderId(String orderId);

    Optional<PaymentIntent> findByOrderIdAndApplicantId(String orderId, Long applicantId);

    Optional<PaymentIntent> findByIdAndApplicantId(Long id, Long applicantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pi from PaymentIntent pi where pi.orderId = :orderId")
    Optional<PaymentIntent> findByOrderIdForUpdate(@Param("orderId") String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pi from PaymentIntent pi where pi.id = :id")
    Optional<PaymentIntent> findByIdForUpdate(@Param("id") Long id);

    List<PaymentIntent> findByStatusInAndUpdatedAtBefore(Collection<PaymentIntent.IntentStatus> statuses, Instant updatedBefore);
}
