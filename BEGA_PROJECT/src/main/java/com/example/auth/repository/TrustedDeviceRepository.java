package com.example.auth.repository;

import com.example.auth.entity.TrustedDevice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, Long> {

    List<TrustedDevice> findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(Long userId);

    Optional<TrustedDevice> findByUserIdAndFingerprintAndRevokedAtIsNull(Long userId, String fingerprint);

    Optional<TrustedDevice> findByUserIdAndFingerprint(Long userId, String fingerprint);

    Optional<TrustedDevice> findByIdAndUserIdAndRevokedAtIsNull(Long id, Long userId);
}
