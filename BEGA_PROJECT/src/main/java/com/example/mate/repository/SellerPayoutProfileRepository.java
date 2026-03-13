package com.example.mate.repository;

import com.example.mate.entity.SellerPayoutProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SellerPayoutProfileRepository extends JpaRepository<SellerPayoutProfile, Long> {

    Optional<SellerPayoutProfile> findByUserIdAndProvider(Long userId, String provider);

    Optional<SellerPayoutProfile> findByProviderAndProviderSellerId(String provider, String providerSellerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select spp from SellerPayoutProfile spp where spp.userId = :userId and spp.provider = :provider")
    Optional<SellerPayoutProfile> findByUserIdAndProviderForUpdate(
            @Param("userId") Long userId,
            @Param("provider") String provider);
}

