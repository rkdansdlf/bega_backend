package com.example.mate.repository;

import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.SettlementStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pt from PaymentTransaction pt where pt.id = :id")
    Optional<PaymentTransaction> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pt from PaymentTransaction pt where pt.orderId = :orderId")
    Optional<PaymentTransaction> findByOrderIdForUpdate(@Param("orderId") String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pt from PaymentTransaction pt where pt.paymentKey = :paymentKey")
    Optional<PaymentTransaction> findByPaymentKeyForUpdate(@Param("paymentKey") String paymentKey);

    Optional<PaymentTransaction> findByOrderId(String orderId);

    Optional<PaymentTransaction> findByPaymentKey(String paymentKey);

    Optional<PaymentTransaction> findByApplicationId(Long applicationId);

    List<PaymentTransaction> findByApplicationIdIn(Collection<Long> applicationIds);

    List<PaymentTransaction> findByOrderIdIn(Collection<String> orderIds);

    @Query("""
            select payment
              from PaymentTransaction payment
             where payment.paymentStatus = :paymentStatus
               and payment.settlementStatus = :settlementStatus
               and exists (
                    select application.id
                      from PartyApplication application
                     where application.id = payment.applicationId
                       and application.isApproved = true)
               and not exists (
                    select payout.id
                      from PayoutTransaction payout
                     where payout.paymentTransactionId = payment.id)
             order by payment.id
            """)
    List<PaymentTransaction> findApprovedWithoutPayout(
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("settlementStatus") SettlementStatus settlementStatus,
            Pageable pageable);
}
