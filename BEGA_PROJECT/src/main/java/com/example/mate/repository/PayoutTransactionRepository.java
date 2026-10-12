package com.example.mate.repository;

import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.SettlementStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PayoutTransactionRepository extends JpaRepository<PayoutTransaction, Long> {

    Optional<PayoutTransaction> findTopByPaymentTransactionIdOrderByIdDesc(Long paymentTransactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pt from PayoutTransaction pt where pt.id = :id")
    Optional<PayoutTransaction> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pt from PayoutTransaction pt where pt.paymentTransactionId = :paymentTransactionId order by pt.id desc")
    Optional<PayoutTransaction> findTopByPaymentTransactionIdForUpdateOrderByIdDesc(
            @Param("paymentTransactionId") Long paymentTransactionId);

    List<PayoutTransaction> findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            Collection<SettlementStatus> statuses,
            Instant nextRetryAt);

    List<PayoutTransaction> findTop100ByStatusAndFailureCodeOrderByIdAsc(
            SettlementStatus status,
            String failureCode);

    boolean existsBySellerIdAndProviderSellerIdIsNullAndStatusIn(
            Long sellerId,
            Collection<SettlementStatus> statuses);

    @Query("""
            SELECT CASE WHEN COUNT(pt) > 0 THEN true ELSE false END
              FROM PayoutTransaction pt
             WHERE pt.sellerId = :sellerId
               AND (TRIM(pt.providerSellerId) IS NULL OR TRIM(pt.providerSellerId) = '')
               AND pt.status IN :statuses
               AND (TRIM(pt.providerCode) IS NULL
                    OR TRIM(pt.providerCode) = ''
                    OR UPPER(TRIM(pt.providerCode)) = 'TOSS')
            """)
    boolean existsUnboundTossPayoutForSeller(
            @Param("sellerId") Long sellerId,
            @Param("statuses") Collection<SettlementStatus> statuses);
}
