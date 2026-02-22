package com.example.mate.repository;

import com.example.mate.entity.PayoutTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PayoutTransactionRepository extends JpaRepository<PayoutTransaction, Long> {

    Optional<PayoutTransaction> findTopByPaymentTransactionIdOrderByIdDesc(Long paymentTransactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pt from PayoutTransaction pt where pt.id = :id")
    Optional<PayoutTransaction> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pt from PayoutTransaction pt where pt.paymentTransactionId = :paymentTransactionId order by pt.id desc")
    Optional<PayoutTransaction> findTopByPaymentTransactionIdForUpdateOrderByIdDesc(
            @Param("paymentTransactionId") Long paymentTransactionId);
}
