package com.example.common.clienterror;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientErrorAlertNotificationRepository extends JpaRepository<ClientErrorAlertNotificationEntity, Long> {

    boolean existsByFingerprintAndNotifiedAtAfter(String fingerprint, LocalDateTime cutoff);

    @Query("""
            select distinct n.fingerprint
            from ClientErrorAlertNotificationEntity n
            where n.fingerprint in :fingerprints
              and n.notifiedAt > :cutoff
            """)
    List<String> findFingerprintsNotifiedAfter(
            @Param("fingerprints") Collection<String> fingerprints,
            @Param("cutoff") LocalDateTime cutoff);

    List<ClientErrorAlertNotificationEntity> findTop10ByNotifiedAtBetweenOrderByNotifiedAtDesc(LocalDateTime from, LocalDateTime to);

    List<ClientErrorAlertNotificationEntity> findByFingerprintInOrderByNotifiedAtDesc(Collection<String> fingerprints);

    long deleteByNotifiedAtBefore(LocalDateTime cutoff);
}
