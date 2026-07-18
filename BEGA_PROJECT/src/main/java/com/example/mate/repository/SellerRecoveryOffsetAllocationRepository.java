package com.example.mate.repository;

import com.example.mate.entity.SellerRecoveryOffsetAllocation;
import com.example.mate.entity.SellerRecoveryOffsetStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SellerRecoveryOffsetAllocationRepository
        extends JpaRepository<SellerRecoveryOffsetAllocation, Long> {

    Optional<SellerRecoveryOffsetAllocation> findByPayoutTransactionIdAndRecoveryId(
            Long payoutTransactionId,
            Long recoveryId);

    @Query("select coalesce(sum(allocation.amount), 0) "
            + "from SellerRecoveryOffsetAllocation allocation "
            + "where allocation.recoveryId = :recoveryId and allocation.status = :status")
    Long sumAmountByRecoveryIdAndStatus(
            @Param("recoveryId") Long recoveryId,
            @Param("status") SellerRecoveryOffsetStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select allocation from SellerRecoveryOffsetAllocation allocation "
            + "where allocation.payoutTransactionId = :payoutTransactionId "
            + "and allocation.status in :statuses order by allocation.id")
    List<SellerRecoveryOffsetAllocation> findByPayoutTransactionIdAndStatusInForUpdate(
            @Param("payoutTransactionId") Long payoutTransactionId,
            @Param("statuses") Collection<SellerRecoveryOffsetStatus> statuses);
}
