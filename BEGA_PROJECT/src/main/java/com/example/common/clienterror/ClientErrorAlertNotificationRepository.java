package com.example.common.clienterror;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientErrorAlertNotificationRepository extends JpaRepository<ClientErrorAlertNotificationEntity, Long> {

    boolean existsByFingerprintAndNotifiedAtAfter(String fingerprint, LocalDateTime cutoff);

    List<ClientErrorAlertNotificationEntity> findTop10ByNotifiedAtBetweenOrderByNotifiedAtDesc(LocalDateTime from, LocalDateTime to);

    List<ClientErrorAlertNotificationEntity> findByFingerprintInOrderByNotifiedAtDesc(Collection<String> fingerprints);

    long deleteByNotifiedAtBefore(LocalDateTime cutoff);
}
