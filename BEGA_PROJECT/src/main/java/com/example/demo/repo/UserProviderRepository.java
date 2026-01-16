package com.example.demo.repo;

import com.example.demo.entity.UserProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface UserProviderRepository extends JpaRepository<UserProvider, Long> {
    Optional<UserProvider> findByProviderAndProviderId(String provider, String providerId);

    List<UserProvider> findByUserId(Long userId);

    Optional<UserProvider> findByUserIdAndProvider(Long userId, String provider);

    void deleteByUserIdAndProvider(Long userId, String provider);
}
