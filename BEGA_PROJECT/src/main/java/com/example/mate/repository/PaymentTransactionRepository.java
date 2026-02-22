package com.example.mate.repository;

import com.example.mate.entity.PaymentTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pt from PaymentTransaction pt where pt.orderId = :orderId")
    Optional<PaymentTransaction> findByOrderIdForUpdate(@Param("orderId") String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pt from PaymentTransaction pt where pt.paymentKey = :paymentKey")
    Optional<PaymentTransaction> findByPaymentKeyForUpdate(@Param("paymentKey") String paymentKey);

    Optional<PaymentTransaction> findByOrderId(String orderId);

    Optional<PaymentTransaction> findByPaymentKey(String paymentKey);

    Optional<PaymentTransaction> findByApplicationId(Long applicationId);

    List<PaymentTransaction> findByOrderIdIn(Collection<String> orderIds);
}
