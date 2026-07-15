package com.example.mate.repository;

import com.example.mate.entity.SellerPayoutRecovery;
import com.example.mate.entity.SellerRecoveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SellerPayoutRecoveryRepository extends JpaRepository<SellerPayoutRecovery, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select recovery from SellerPayoutRecovery recovery "
            + "where recovery.sourcePaymentTransactionId = :sourcePaymentTransactionId")
    Optional<SellerPayoutRecovery> findBySourcePaymentTransactionIdForUpdate(
            @Param("sourcePaymentTransactionId") Long sourcePaymentTransactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select recovery from SellerPayoutRecovery recovery "
            + "where recovery.sellerUserId = :sellerUserId and recovery.status <> :recoveredStatus "
            + "order by recovery.id asc")
    List<SellerPayoutRecovery> findOutstandingBySellerUserIdForUpdate(
            @Param("sellerUserId") Long sellerUserId,
            @Param("recoveredStatus") SellerRecoveryStatus recoveredStatus);

    default List<SellerPayoutRecovery> findOutstandingBySellerUserIdForUpdate(Long sellerUserId) {
        return findOutstandingBySellerUserIdForUpdate(sellerUserId, SellerRecoveryStatus.RECOVERED);
    }
}
