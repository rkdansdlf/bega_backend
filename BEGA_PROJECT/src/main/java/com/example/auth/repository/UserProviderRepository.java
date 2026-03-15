package com.example.auth.repository;

import com.example.auth.entity.UserProvider;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.List;

public interface UserProviderRepository extends JpaRepository<UserProvider, Long> {
    Optional<UserProvider> findByProviderAndProviderId(String provider, String providerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT up FROM UserProvider up WHERE up.provider = :provider AND up.providerId = :providerId")
    Optional<UserProvider> findByProviderAndProviderIdForUpdate(
            @org.springframework.data.repository.query.Param("provider") String provider,
            @org.springframework.data.repository.query.Param("providerId") String providerId);

    List<UserProvider> findByUserId(Long userId);

    Optional<UserProvider> findByUserIdAndProvider(Long userId, String provider);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT up FROM UserProvider up WHERE up.user.id = :userId AND up.provider = :provider")
    Optional<UserProvider> findByUserIdAndProviderForUpdate(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("provider") String provider);

    void deleteByUserIdAndProvider(Long userId, String provider);
}
